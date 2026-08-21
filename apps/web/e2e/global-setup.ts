import { execSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * E2E 全局数据准备（真实后端 API + 真实 MySQL/MinIO；独立 e2e_web_* 数据，teardown 清理）。
 * 原则：测试账号/数据全部临时创建，不写入仓库、不污染既有业务数据。
 */

const BASE = process.env.E2E_BACKEND ?? 'http://localhost:8080'
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
  const data = await api<{ accessToken: string; refreshToken: string; user: unknown }>(null, 'POST', '/api/v1/user/auth/login', {
    account,
    password,
  })
  return data.accessToken
}

function sql(query: string): void {
  execSync(`docker exec -i yunsie-mysql mysql -uroot -pdevpassword -D yunsie_platform -e "${query}"`, {
    stdio: 'ignore',
  })
}

/** 查询并返回输出（用于清理前捕获 id 列表） */
function sqlOut(query: string): string {
  try {
    return execSync(`docker exec -i yunsie-mysql mysql -uroot -pdevpassword -D yunsie_platform -N -e "${query}"`, {
      encoding: 'utf-8',
    })
  } catch {
    return ''
  }
}

/** 幂等前置清理：删除上次失败运行残留的 E2E 数据（按名称模式，不触碰其他业务数据）。
 *  Stage 2.1：dev 库并存 Demo 数据——学习档案与通知按 e2e 用户过滤，MinIO 仅清 e2e 课程目录。 */
function cleanLeftovers(): void {
  const learners = `(SELECT id FROM user_account WHERE username LIKE 'e2e_web_%' OR username LIKE 'e2e_admin_%')`
  const exams = `(SELECT id FROM exam_exam WHERE name IN ('E2E Seed Exam','E2E UI Exam'))`
  const questions = `(SELECT id FROM question_question WHERE stem LIKE 'E2E Web Q%' OR stem LIKE 'E2E Imported%' OR stem LIKE 'E2E Admin Q%')`
  const course = `(SELECT id FROM course_course WHERE title='E2E Course' OR title LIKE 'E2E Admin Course%')`
  // MinIO：仅清理残留 e2e 课程的视频对象（先取 id，再逐目录删除）
  for (const cid of sqlOut(`SELECT GROUP_CONCAT(id) FROM course_course WHERE title='E2E Course' OR title LIKE 'E2E Admin Course%'`).trim().split(',')) {
    if (cid && /^\d+$/.test(cid)) {
      try {
        execSync(`docker exec yunsie-minio sh -c "rm -rf /data/yunsie-videos/course/${cid}"`, { stdio: 'ignore' })
      } catch {
        // MinIO 容器不可用时不阻塞
      }
    }
  }
  sql(`DELETE FROM notify_message WHERE user_id IN ${learners};`)
  sql(`DELETE FROM learn_study_calendar WHERE user_id IN ${learners};`)
  sql(`DELETE FROM learn_mastery WHERE user_id IN ${learners};`)
  sql(`DELETE FROM learn_profile_summary WHERE user_id IN ${learners};`)
  sql(`DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE exam_id IN ${exams});`)
  sql(`DELETE FROM exam_attempt WHERE exam_id IN ${exams};`)
  sql(`DELETE FROM exam_paper_option WHERE paper_question_id IN (SELECT id FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN ${exams}));`)
  sql(`DELETE FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN ${exams});`)
  sql(`DELETE FROM exam_paper WHERE exam_id IN ${exams};`)
  sql(`DELETE FROM exam_exam WHERE name IN ('E2E Seed Exam','E2E UI Exam');`)
  sql(`DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='e2e_admin_teacher_role');`)
  sql(`DELETE FROM sys_role WHERE role_code='e2e_admin_teacher_role';`)
  sql(`DELETE FROM question_practice_record WHERE user_id IN ${learners};`)
  sql(`DELETE FROM question_mistake WHERE user_id IN ${learners};`)
  sql(`DELETE FROM question_knowledge_node WHERE question_id IN ${questions};`)
  sql(`DELETE FROM question_option WHERE question_id IN ${questions};`)
  sql(`DELETE FROM question_question WHERE stem LIKE 'E2E Web Q%' OR stem LIKE 'E2E Imported%' OR stem LIKE 'E2E Admin Q%';`)
  sql(`DELETE FROM course_learn_progress WHERE lesson_id IN (SELECT id FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN ${course}));`)
  sql(`DELETE FROM course_lesson_knowledge_node WHERE lesson_id IN (SELECT id FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN ${course}));`)
  sql(`DELETE FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN ${course});`)
  sql(`DELETE FROM course_chapter WHERE course_id IN ${course};`)
  sql(`DELETE FROM course_course WHERE title='E2E Course' OR title LIKE 'E2E Admin Course%';`)
  sql(`DELETE FROM subject_knowledge_node WHERE code IN ('kp-web-e2e','ch-web-e2e');`)
  sql(`DELETE FROM subject_subject WHERE code='subj-web-e2e';`)
  sql(`DELETE FROM subject_version WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-web-e2e' OR code='cert-switch-e2e');`)
  sql(`DELETE FROM certificate_cert WHERE code='cert-web-e2e' OR code='cert-switch-e2e';`)
  sql(`DELETE FROM certificate_category WHERE code='cat-web-e2e' OR code='cat-switch-e2e';`)
  sql(`DELETE FROM sys_user_role WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_refresh_token WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_profile WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_credential WHERE user_id IN ${learners};`)
  sql(`DELETE FROM learn_user_goal WHERE user_id IN ${learners};`)
  sql(`DELETE FROM user_account WHERE username LIKE 'e2e_web_%' OR username LIKE 'e2e_admin_%';`)
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export default async function globalSetup(): Promise<void> {
  cleanLeftovers()
  const admin = await login('admin', 'Admin@123456')

  // 1) 独立 E2E 账号
  const learnerId = await post<number>(admin, '/api/v1/user/accounts', {
    username: 'e2e_web_learner',
    mobile: null,
    nickname: 'E2E 学员',
    password: 'Learner@1234',
    userType: 1,
    roleIds: null,
  })
  const otherId = await post<number>(admin, '/api/v1/user/accounts', {
    username: 'e2e_web_other',
    mobile: null,
    nickname: 'E2E 学员B',
    password: 'Other@1234',
    userType: 1,
    roleIds: null,
  })
  const learnerToken = await login('e2e_web_learner', 'Learner@1234')

  // 2) 知识链（证书/版本/科目/章/知识点）
  const categoryId = await post<number>(admin, '/api/v1/certificate/categories', {
    parentId: 0,
    name: 'E2E Cat',
    code: 'cat-web-e2e',
  })
  const certId = await post<number>(admin, '/api/v1/certificate/certificates', {
    categoryId,
    name: 'E2E Cert',
    code: 'cert-web-e2e',
  })
  const versionId = await post<number>(admin, '/api/v1/subject/versions', {
    certificateId: certId,
    versionNo: '2026',
    name: 'v2026',
  })
  await api(admin, 'PUT', `/api/v1/subject/versions/${versionId}/current`)
  const subjectId = await post<number>(admin, '/api/v1/subject/subjects', {
    certificateId: certId,
    name: 'E2E Subj',
    code: 'subj-web-e2e',
  })
  const chapterNodeId = await post<number>(admin, '/api/v1/subject/nodes', {
    versionId,
    subjectId,
    parentId: 0,
    nodeType: 1,
    name: 'E2E Ch',
    code: 'ch-web-e2e',
  })
  const kpId = await post<number>(admin, '/api/v1/subject/nodes', {
    versionId,
    subjectId,
    parentId: chapterNodeId,
    nodeType: 2,
    name: 'E2E KP',
    code: 'kp-web-e2e',
  })

  // 第二科目（Stage 2.1 目标切换测试用）
  const subjectBId = await post<number>(admin, '/api/v1/subject/subjects', {
    certificateId: certId,
    name: 'E2E Subj B',
    code: 'subj-web-e2e-b',
  })

  // 学员考试目标（首页/课程/练习/考试的主要上下文；Stage 2.2 只选证书）
  await api(learnerToken, 'POST', '/api/v1/learn/goals/select', { certificateId: certId })
  const otherToken = await login('e2e_web_other', 'Other@1234')
  await api(otherToken, 'POST', '/api/v1/learn/goals/select', { certificateId: certId })

  // 3) 两道已发布单选题（答案 A）
  const questionIds: number[] = []
  for (const stem of ['E2E Web Q1', 'E2E Web Q2']) {
    const qid = await post<number>(admin, '/api/v1/question/questions', {
      certificateId: certId,
      questionType: 1,
      stem,
      analysis: 'A only',
      answer: 'A',
      difficulty: 2,
      source: 2,
      options: [
        { optionKey: 'A', content: 'OptA' },
        { optionKey: 'B', content: 'OptB' },
      ],
      nodeIds: [kpId],
    })
    await post(admin, `/api/v1/question/questions/${qid}/submit-review`, {})
    await post(admin, `/api/v1/question/questions/${qid}/approve`, {})
    questionIds.push(qid)
  }

  async function publishExam(name: string): Promise<number> {
    const examId = await post<number>(admin, '/api/v1/exam/exams', {
      certificateId: certId,
      name,
      durationMinutes: 60,
      passScore: 60,
      rule: { questionCount: 2, questionTypes: [1] },
    })
    await post(admin, `/api/v1/exam/exams/${examId}/assemble`, {})
    await post(admin, `/api/v1/exam/exams/${examId}/publish`, {})
    return examId
  }

  // 4) 考试 A：setup 内直接交卷（播种成绩通知/学习档案/考试历史）；考试 B：留给 UI 考试流程
  const examSeedId = await publishExam('E2E Seed Exam')
  const examUiId = await publishExam('E2E UI Exam')

  // 5) 课程：章 + 小节1（真实视频上传 MinIO）+ 小节2（无视频）+ 发布
  const courseId = await post<number>(admin, '/api/v1/course/courses', {
    certificateId: certId,
    teacherId: 2,
    title: 'E2E Course',
    description: 'E2E course desc',
  })
  const chapterId = await post<number>(admin, '/api/v1/course/chapters', {
    courseId,
    title: 'E2E Chapter',
    sort: 0,
  })
  const lesson1Id = await post<number>(admin, '/api/v1/course/lessons', {
    chapterId,
    title: 'E2E Lesson One',
    durationSeconds: 30,
    sort: 0,
  })
  // 章节练习题目范围（Stage 2.1）：第 1 章首讲关联知识点
  await api(admin, 'PUT', `/api/v1/course/lessons/${lesson1Id}/knowledge-nodes`, { nodeIds: [kpId] })
  const lesson2Id = await post<number>(admin, '/api/v1/course/lessons', {
    chapterId,
    title: 'E2E Lesson Two',
    durationSeconds: 30,
    sort: 1,
  })

  // 视频上传（multipart，真实 MinIO 落库）
  const videoBytes = readFileSync(join(__dirname, 'fixtures', 'flower.mp4'))
  const form = new FormData()
  form.append('file', new Blob([videoBytes], { type: 'video/mp4' }), 'flower.mp4')
  const uploadResp = await fetch(`${BASE}/api/v1/course/lessons/${lesson1Id}/video`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin}` },
    body: form,
  })
  const uploadJson = (await uploadResp.json()) as Result<unknown>
  if (uploadJson.code !== 0) {
    throw new Error(`video upload failed: ${uploadJson.message}`)
  }

  await post(admin, `/api/v1/course/courses/${courseId}/publish`, {})

  // 6) 学员播种行为：练习 1 次（答错 → 薄弱点/错题）、课时进度 5s
  await post(learnerToken, '/api/v1/question/practice/submit', {
    questionId: questionIds[0],
    answer: 'B',
    mode: 3,
    nodeId: kpId,
    responseTimeMs: 2000,
  })
  await post(learnerToken, '/api/v1/course/progress', { lessonId: lesson1Id, positionSeconds: 5 })

  // 7) 考试 A 交卷（经 API 走真实交卷链路，触发异步事件）
  const start = await post<{ attemptId: number; questions: { paperQuestionId: number; options: { optionKey: string }[] }[] }>(
    learnerToken,
    `/api/v1/exam/exams/${examSeedId}/attempts`,
  )
  const answers = start.questions.map((q) => ({
    paperQuestionId: q.paperQuestionId,
    answer: q.options[0].optionKey,
  }))
  await api(learnerToken, 'PUT', `/api/v1/exam/attempts/${start.attemptId}/answers`, { answers })
  await post(learnerToken, `/api/v1/exam/attempts/${start.attemptId}/submit`, {})

  // 8) 等待异步消费者完成（成绩通知 + 学习档案同步）
  let ready = false
  for (let i = 0; i < 30 && !ready; i++) {
    const unread = await api<number>(learnerToken, 'GET', '/api/v1/notify/messages/unread-count')
    const summary = await api<{ examCount: number; practiceCount: number }>(learnerToken, 'GET', '/api/v1/learn/me/summary')
    ready = unread >= 1 && summary.examCount >= 1 && summary.practiceCount >= 1
    if (!ready) await sleep(500)
  }
  if (!ready) {
    throw new Error('E2E setup: async consumers did not converge (notify/learn)')
  }

  // ---- 管理端 E2E 数据（e2e_admin_*）----
  // 教师（course 权限 + learn:profile:view）与普通学员
  const teacher1Id = await post<number>(admin, '/api/v1/user/accounts', {
    username: 'e2e_admin_teacher',
    mobile: null,
    nickname: 'E2E 教师A',
    password: 'Teacher@1234',
    userType: 2,
    roleIds: null,
  })
  const teacher2Id = await post<number>(admin, '/api/v1/user/accounts', {
    username: 'e2e_admin_teacher2',
    mobile: null,
    nickname: 'E2E 教师B',
    password: 'Teacher2@1234',
    userType: 2,
    roleIds: null,
  })
  const adminLearnerId = await post<number>(admin, '/api/v1/user/accounts', {
    username: 'e2e_admin_learner',
    mobile: null,
    nickname: 'E2E 管理学员',
    password: 'Learner@1234',
    userType: 1,
    roleIds: null,
  })
  const adminLearnerToken = await login('e2e_admin_learner', 'Learner@1234')

  // 教师角色：course 全权限 + learn:profile:view + 题目/考试只读
  const roleId = await post<number>(admin, '/api/v1/sys/roles', {
    roleCode: 'e2e_admin_teacher_role',
    roleName: 'E2E 教师角色',
    roleType: 2,
    status: 1,
    remark: '',
    sort: 0,
  })
  const permTree = await api<{ id: number; permissionCode: string; children: unknown[] }[]>(admin, 'GET', '/api/v1/sys/permissions/tree')
  const flatPerms: { id: number; permissionCode: string }[] = []
  const walkPerms = (nodes: typeof permTree) => {
    for (const n of nodes) {
      flatPerms.push({ id: n.id, permissionCode: n.permissionCode })
      walkPerms((n.children ?? []) as typeof permTree)
    }
  }
  walkPerms(permTree)
  const wantCodes = ['course:course:create', 'course:course:update', 'course:course:delete', 'course:course:read', 'course:course:publish', 'course:video:upload', 'learn:profile:view', 'question:question:read', 'exam:exam:read']
  const wantIds = flatPerms.filter((p) => wantCodes.includes(p.permissionCode)).map((p) => p.id)
  await post(admin, `/api/v1/sys/roles/${roleId}/permissions`, { permissionIds: wantIds })
  await post(admin, `/api/v1/user/accounts/${teacher1Id}/roles`, { roleIds: [roleId] })
  await post(admin, `/api/v1/user/accounts/${teacher2Id}/roles`, { roleIds: [roleId] })

  // 课程 A（教师A 名下，含章节+小节+学员进度）、课程 B（教师B 名下）
  const courseAId = await post<number>(admin, '/api/v1/course/courses', {
    certificateId: certId,
    teacherId: teacher1Id,
    title: 'E2E Admin Course A',
    description: 'admin course A',
  })
  const chapterAId = await post<number>(admin, '/api/v1/course/chapters', { courseId: courseAId, title: 'Admin Chapter A', sort: 0 })
  const lessonAId = await post<number>(admin, '/api/v1/course/lessons', { chapterId: chapterAId, title: 'Admin Lesson A', durationSeconds: 600, sort: 0 })
  const courseBId = await post<number>(admin, '/api/v1/course/courses', {
    certificateId: certId,
    teacherId: teacher2Id,
    title: 'E2E Admin Course B',
    description: 'admin course B',
  })
  // 课程 A 发布（学员进度上报前置：仅已发布课程可学习）；随后下架 → 教师内容管理测试从草稿态开始
  await post(admin, `/api/v1/course/courses/${courseAId}/publish`, {})
  await post(adminLearnerToken, '/api/v1/course/progress', { lessonId: lessonAId, positionSeconds: 60 })
  await post(admin, `/api/v1/course/courses/${courseAId}/unpublish`, {})

  // Excel 导入 fixture（真实 xlsx；知识点编码 = kp-web-e2e）
  const XLSX = await import('xlsx')
  const sheet = XLSX.utils.aoa_to_sheet([
    ['题型', '题干', '选项A', '选项B', '选项C', '选项D', '选项E', '选项F', '答案', '解析', '难度', '来源', '知识点编码'],
    ['单选', 'E2E Imported Question', 'OptA', 'OptB', '', '', '', '', 'A', 'A only', '中', '模拟题', 'kp-web-e2e'],
  ])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, sheet, '题库导入模板')
  writeFileSync(join(__dirname, 'fixtures', 'questions.xlsx'), XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }))

  // 10) 清理历史残留（幂等：重复 setup 不冲突）
  const state = {
    learnerId,
    otherId,
    categoryId,
    certId,
    versionId,
    subjectId,
    chapterNodeId,
    kpId,
    questionIds,
    examSeedId,
    examUiId,
    courseId,
    chapterId,
    lesson1Id,
    lesson2Id,
    teacher1Id,
    teacher2Id,
    adminLearnerId,
    roleId,
    courseAId,
    chapterAId,
    lessonAId,
    courseBId,
  }
  writeFileSync(join(__dirname, '.state.json'), JSON.stringify(state, null, 2))
  console.log('[global-setup] done', JSON.stringify(state))
}
