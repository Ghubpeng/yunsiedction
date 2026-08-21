import { execSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * 部署冒烟数据准备：全部 API 请求走生产链路 nginx（5173 → server:8080），
 * 不直连 8080（冒烟目标就是生产形态）。独立 deploy_* / Deploy* 数据，teardown 清理。
 */
const BASE = process.env.E2E_BACKEND ?? 'http://localhost:5173'
const __dirname = dirname(fileURLToPath(import.meta.url))

interface Result<T> {
  code: number
  message: string
  data: T
  traceId: string
}

async function api<T>(token: string | null, method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const resp = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = (await resp.json()) as Result<T>
  if (json.code !== 0) {
    throw new Error(`${method} ${path} failed: code=${json.code} message=${json.message}`)
  }
  return json.data
}

async function post<T>(token: string, path: string, body?: unknown): Promise<T> {
  return api<T>(token, 'POST', path, body)
}

async function login(account: string, password: string): Promise<string> {
  const data = await api<{ accessToken: string }>(null, 'POST', '/api/v1/user/auth/login', { account, password })
  return data.accessToken
}

function sql(query: string): void {
  execSync(`docker exec -i yunsie-mysql mysql -uroot -pdevpassword -D yunsie_platform -e "${query}"`, {
    stdio: 'ignore',
  })
}

/** 查询并返回输出（用于清理前捕获课程 id 列表） */
function sqlOut(query: string): string {
  try {
    return execSync(`docker exec -i yunsie-mysql mysql -uroot -pdevpassword -D yunsie_platform -N -e "${query}"`, {
      encoding: 'utf-8',
    })
  } catch {
    return ''
  }
}

/** 幂等前置清理：删除上次失败运行残留的部署冒烟数据（名称模式，不触碰其他业务数据）。
 *  Stage 2.1：dev 库并存 Demo 数据——MinIO 仅清 deploy 课程目录。 */
function cleanLeftovers(): void {
  const learners = `(SELECT id FROM user_account WHERE username LIKE 'deploy\\_%')`
  const courses = `(SELECT id FROM course_course WHERE title='Deploy Course')`
  // MinIO：仅清理残留 deploy 课程的视频对象（先取 id，再逐目录删除；Demo 视频不受影响）
  for (const cid of sqlOut(`SELECT GROUP_CONCAT(id) FROM course_course WHERE title='Deploy Course'`).trim().split(',')) {
    if (cid && /^\d+$/.test(cid)) {
      try {
        execSync(`docker exec yunsie-minio sh -c "rm -rf /data/yunsie-videos/course/${cid}"`, { stdio: 'ignore' })
      } catch {
        // MinIO 容器不可用时不阻塞
      }
    }
  }
  sql(`DELETE FROM course_learn_progress WHERE lesson_id IN (SELECT id FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN ${courses}));`)
  sql(`DELETE FROM course_lesson_knowledge_node WHERE lesson_id IN (SELECT id FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN ${courses}));`)
  sql(`DELETE FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN ${courses});`)
  sql(`DELETE FROM course_chapter WHERE course_id IN ${courses};`)
  sql(`DELETE FROM course_course WHERE title='Deploy Course';`)
  // 冒烟曾为 demo 账号建立 Deploy 目标：一并清理（避免污染演示体验的当前目标）
  sql(`DELETE FROM learn_user_goal WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-deploy');`)
  sql(`DELETE FROM certificate_cert WHERE code='cert-deploy';`)
  sql(`DELETE FROM certificate_category WHERE code='cat-deploy';`)
  sql(`DELETE FROM sys_user_role WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_refresh_token WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_profile WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_credential WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_account WHERE username LIKE 'deploy\\_%';`)
}

export default async function deploySetup(): Promise<void> {
  cleanLeftovers()
  // 管理员经 nginx 登录（验证反代链路）
  const admin = await login('admin', 'Admin@123456')

  // 知识链最小闭环：分类 + 证书 + 版本 + 科目 + 知识点（课程创建/章节练习/考试目标前置）
  const categoryId = await post<number>(admin, '/api/v1/certificate/categories', {
    parentId: 0,
    name: 'Deploy Cat',
    code: 'cat-deploy',
  })
  const certId = await post<number>(admin, '/api/v1/certificate/certificates', {
    categoryId,
    name: 'Deploy Cert',
    code: 'cert-deploy',
  })
  const versionId = await post<number>(admin, '/api/v1/subject/versions', {
    certificateId: certId,
    versionNo: '2026',
    name: 'v2026',
  })
  await api(admin, 'PUT', `/api/v1/subject/versions/${versionId}/current`)
  const subjectId = await post<number>(admin, '/api/v1/subject/subjects', {
    certificateId: certId,
    name: 'Deploy Subj',
    code: 'subj-deploy',
  })
  const chapterNodeId = await post<number>(admin, '/api/v1/subject/nodes', {
    versionId,
    subjectId,
    parentId: 0,
    nodeType: 1,
    name: 'Deploy Ch',
    code: 'ch-deploy',
  })
  const kpId = await post<number>(admin, '/api/v1/subject/nodes', {
    versionId,
    subjectId,
    parentId: chapterNodeId,
    nodeType: 2,
    name: 'Deploy KP',
    code: 'kp-deploy',
  })

  // 课程 + 章 + 小节（视频在冒烟用例内经 nginx 上传，验证 multipart 反代链路）
  const courseId = await post<number>(admin, '/api/v1/course/courses', {
    certificateId: certId,
    subjectId,
    versionId,
    teacherId: 2,
    title: 'Deploy Course',
    description: 'deploy smoke course',
  })
  const chapterId = await post<number>(admin, '/api/v1/course/chapters', {
    courseId,
    title: 'Deploy Chapter',
    sort: 0,
  })
  const lessonId = await post<number>(admin, '/api/v1/course/lessons', {
    chapterId,
    title: 'Deploy Lesson',
    durationSeconds: 30,
    sort: 0,
  })
  await api(admin, 'PUT', `/api/v1/course/lessons/${lessonId}/knowledge-nodes`, { nodeIds: [kpId] })
  // 不在此发布：冒烟用例 10 走"草稿上传视频 → 发布"真实业务顺序（已发布课程禁止替换视频）

  // 部署学员账号（浏览器登录冒烟用）
  await post<number>(admin, '/api/v1/user/accounts', {
    username: 'deploy_learner',
    mobile: null,
    nickname: 'Deploy 学员',
    password: 'Deploy@1234',
    userType: 1,
    roleIds: null,
  })

  // 保证 demo 账号存在（冒烟第 15 项 Demo 一键登录；demo.ps1 已播种时重复创建会失败，忽略即可）
  try {
    await post<number>(admin, '/api/v1/user/accounts', {
      username: 'demo_learner',
      mobile: null,
      nickname: '演示学员',
      password: 'Demo@123456',
      userType: 1,
      roleIds: null,
    })
  } catch {
    // 已存在（demo.ps1 播种）→ 无需处理
  }
  // Demo 一键登录后直接进入学习首页（考试目标为冒烟链路上下文；Stage 2.2 只选证书）
  const demoToken = await login('demo_learner', 'Demo@123456')
  try {
    await api(demoToken, 'POST', '/api/v1/learn/goals/select', { certificateId: certId })
  } catch {
    // 已有目标（demo.ps1 播种）→ 忽略
  }

  const state = {
    learnerAccount: 'deploy_learner',
    learnerPassword: 'Deploy@1234',
    categoryId,
    certId,
    courseId,
    chapterId,
    lessonId,
  }
  writeFileSync(join(__dirname, '.deploy-state.json'), JSON.stringify(state, null, 2))
  console.log('[deploy-setup] done', JSON.stringify(state))
}
