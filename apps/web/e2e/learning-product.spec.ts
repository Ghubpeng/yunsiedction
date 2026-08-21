import { expect, test, type APIRequestContext } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { LEARNER, uiLogin } from './helpers'

/**
 * Stage 2.1 学习产品化 E2E（真实后端 + MySQL + MinIO）：
 * 考试目标 / 内容树过滤 / 课程→章节练习闭环 / 我的资产 / AI 诚实降级 / Demo 三角色与权限隔离。
 * 数据来自 global-setup（e2e_* 前缀，teardown 清理）。
 */
const __dirname = dirname(fileURLToPath(import.meta.url))
const state = JSON.parse(readFileSync(join(__dirname, '.state.json'), 'utf-8')) as {
  courseId: number
  chapterId: number
  lesson1Id: number
  lesson2Id: number
}

/** 演示账号的当前目标可能被 deploy 冒烟重置（无当前目标 → 首页出现证书引导）：
 *  自愈：等待进入首页后轮询——出现引导则选择第一个证书恢复学习首页（不依赖特定演示证书）。 */
async function ensureDemoGoal(page: import('@playwright/test').Page): Promise<void> {
  await page.waitForURL(/\/$/, { timeout: 15_000 })
  for (let i = 0; i < 20; i++) {
    const hello = page.getByRole('heading', { name: /你好/ })
    if (await hello.isVisible().catch(() => false)) return
    const certBtn = page.locator('section[aria-label="选择考试"] button').first()
    if (await certBtn.isVisible().catch(() => false)) {
      await certBtn.click()
      await page.getByRole('button', { name: /开始准备/ }).click()
    }
    await page.waitForTimeout(500)
  }
}

async function api<T>(request: APIRequestContext, token: string | null, method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const resp = await request.fetch(`http://localhost:5173${path}`, {
    method,
    headers,
    data: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = (await resp.json()) as { code: number; message: string; data: T }
  if (json.code !== 0) throw new Error(`${method} ${path} failed: ${json.message}`)
  return json.data
}

async function loginViaApi(request: APIRequestContext, account: string, password: string): Promise<string> {
  const data = await api<{ accessToken: string }>(request, null, 'POST', '/api/v1/user/auth/login', { account, password })
  return data.accessToken
}

/** 完成课程全部课时（真实进度接口；章节完成由服务端判定） */
async function finishAllLessons(request: APIRequestContext, token: string): Promise<void> {
  await api(request, token, 'POST', '/api/v1/course/progress', { lessonId: state.lesson1Id, positionSeconds: 30 })
  await api(request, token, 'POST', '/api/v1/course/progress', { lessonId: state.lesson2Id, positionSeconds: 30 })
}

test('首次进入：无目标只选证书 → 一键开始准备，首页出现目标上下文', async ({ page, request }) => {
  // 独立新用户（无目标；teardown 按 e2e_ 前缀清理）
  const admin = await loginViaApi(request, 'admin', 'Admin@123456')
  await api(request, admin, 'POST', '/api/v1/user/accounts', {
    username: 'e2e_goal_fresh',
    mobile: null,
    nickname: '目标新学员',
    password: 'Fresh@1234',
    userType: 1,
    roleIds: null,
  })
  await uiLogin(page, 'e2e_goal_fresh', 'Fresh@1234')
  // 无目标 → 引导只选证书（不要求科目）
  await expect(page.getByRole('heading', { name: '你准备参加什么考试？' })).toBeVisible()
  await page.getByLabel('搜索考试').fill('E2E')
  await page.getByRole('button', { name: 'E2E Cert', exact: true }).click()
  await page.getByRole('button', { name: /开始准备/ }).click()
  await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText(/当前考试：E2E Cert/)).toBeVisible()
})

test('切换考试目标：历史保留、学习数据不污染', async ({ page, request }) => {
  // 自建第二证书（不依赖演示数据；teardown 按 e2e 前缀清理）
  const admin = await loginViaApi(request, 'admin', 'Admin@123456')
  const categoryId = await api<number>(request, admin, 'POST', '/api/v1/certificate/categories', {
    parentId: 0,
    name: 'E2E Switch Cat',
    code: 'cat-switch-e2e',
  })
  const certBId = await api<number>(request, admin, 'POST', '/api/v1/certificate/certificates', {
    categoryId,
    name: 'E2E Switch Cert',
    code: 'cert-switch-e2e',
  })
  const versionId = await api<number>(request, admin, 'POST', '/api/v1/subject/versions', {
    certificateId: certBId,
    versionNo: '2026',
    name: 'v2026',
  })
  await api(request, admin, 'PUT', `/api/v1/subject/versions/${versionId}/current`)

  await uiLogin(page, LEARNER.account, LEARNER.password)
  await page.goto('/profile')
  await expect(page.getByText('E2E Cert').first()).toBeVisible()
  await page.getByRole('link', { name: '切换考试' }).click()
  await page.getByRole('button', { name: 'E2E Switch Cert', exact: true }).click()
  await page.getByRole('button', { name: /开始准备/ }).click()
  await expect(page).toHaveURL(/\/profile$/)
  await expect(page.getByText('E2E Switch Cert').first()).toBeVisible()
  // 历史目标保留展示
  await expect(page.getByText(/历史目标：E2E Cert/)).toBeVisible()
  // 既有学习数据未被污染
  await expect(page.getByText('学习概览')).toBeVisible()
})

test('课程按目标过滤：目标证书课程出现在列表', async ({ page }) => {
  await uiLogin(page, LEARNER.account, LEARNER.password)
  await page.goto('/courses')
  await expect(page.getByText('E2E Course').first()).toBeVisible()
  await expect(page.getByText('继续学习', { exact: true })).toBeVisible()
})

test('课程→章节练习闭环：完成后出现入口，判分由后端，掌握反馈后回主线', async ({ page, request }) => {
  const token = await loginViaApi(request, LEARNER.account, LEARNER.password)
  await finishAllLessons(request, token)

  await uiLogin(page, LEARNER.account, LEARNER.password)
  await page.goto(`/courses/${state.courseId}`)
  await expect(page.getByText('已完成').first()).toBeVisible()
  // Stage 2.4：视频完成后出现章节练习入口（未练习=已完成态；练习过=掌握态，可再练）
  await expect(page.getByText(/学完这一章了|本章已掌握/)).toBeVisible()
  await page.getByRole('link', { name: /开始章节练习|再练一次/ }).click()
  await expect(page).toHaveURL(/\/chapters\/\d+\/practice$/)
  await expect(page.getByText(/第 1 \/ \d+ 题/)).toBeVisible()
  // 逐题作答（服务端判分，前端不复制判分逻辑）；完成后进入掌握反馈页
  let guard = 0
  while (guard < 20) {
    const submitBtn = page.getByRole('button', { name: '提交答案' })
    if ((await submitBtn.count()) === 0) break
    await page.locator('.option-item').first().click()
    await submitBtn.click()
    await expect(page.getByText(/回答正确|回答错误/)).toBeVisible()
    await page.getByRole('button', { name: /下一题|查看本章反馈/ }).click()
    guard++
  }
  await expect(page.getByText(/本章练习 \d+\/\d+ 正确/)).toBeVisible()
  await page.getByRole('link', { name: /返回课程，继续下一章/ }).click()
  await expect(page).toHaveURL(new RegExp(`/courses/${state.courseId}$`))
})

test('我的课程分类（已完成）+ 我的资产空态（不伪造 pay/证书）', async ({ page, request }) => {
  const token = await loginViaApi(request, LEARNER.account, LEARNER.password)
  await finishAllLessons(request, token)
  await uiLogin(page, LEARNER.account, LEARNER.password)
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: '已完成' })).toBeVisible()
  await expect(page.getByText('E2E Course').first()).toBeVisible()
  await expect(page.getByText('暂无已购课程')).toBeVisible()
  await expect(page.getByText('开通课程后将在这里显示。')).toBeVisible()
  await expect(page.getByText('暂无已获得证书')).toBeVisible()
})

test('联系客服页（公开）+ 后台配置实时生效', async ({ page }) => {
  // 公开访问（未登录）
  await page.goto('/service')
  await expect(page.getByRole('heading', { name: '联系客服' })).toBeVisible()
  await expect(page.getByText('常见问题')).toBeVisible()
  await expect(page.getByText(/如何开始学习？/)).toBeVisible()

  // 后台配置电话 → 用户端实时反映
  await uiLogin(page, 'admin', 'Admin@123456')
  await page.goto('/admin/service')
  await page.getByLabel('客服电话（用户端点击可拨打）').fill('400-800-1234')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('客服配置已保存')).toBeVisible()
  await page.goto('/service')
  await expect(page.getByText('400-800-1234')).toBeVisible()
})

test('AI 助手诚实降级：展示上下文且明确 AI 未启用', async ({ page }) => {
  await uiLogin(page, LEARNER.account, LEARNER.password)
  await page.goto(`/courses/${state.courseId}`)
  await page.getByRole('button', { name: '✦ 学习助手' }).click()
  await expect(page.getByText('已携带上下文')).toBeVisible()
  await expect(page.getByText(/AI 助手将在 AI 服务启用后提供智能讲解/)).toBeVisible()
})

test('Demo 三角色（仅 dev）：学员/教师/管理员体验与权限隔离', async ({ browser }) => {
  // 三个角色各自独立浏览器上下文（真实登录接口，互不串扰会话）
  // 登录页出现三角色入口（dev 构建）
  const probe = await browser.newPage()
  await probe.goto('/login')
  await expect(probe.getByRole('button', { name: /学员体验/ })).toBeVisible()
  await expect(probe.getByRole('button', { name: '教师体验' })).toBeVisible()
  await expect(probe.getByRole('button', { name: '管理员体验' })).toBeVisible()
  await probe.close()

  // 教师体验：教师 DataScope（有课程管理，无用户管理）
  {
    const ctx = await browser.newContext()
    const page = await ctx.newPage()
    await page.goto('/login')
    await page.getByRole('button', { name: '教师体验' }).click()
    await ensureDemoGoal(page)
    await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible({ timeout: 15_000 })
    await page.goto('/admin')
    await expect(page.getByRole('link', { name: '课程管理' })).toBeVisible()
    await expect(page.getByRole('link', { name: '用户管理' })).toHaveCount(0)
    await ctx.close()
  }

  // 学员体验：无管理后台权限
  {
    const ctx = await browser.newContext()
    const page = await ctx.newPage()
    await page.goto('/login')
    await page.getByRole('button', { name: /学员体验/ }).click()
    await ensureDemoGoal(page)
    await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible({ timeout: 15_000 })
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/admin$/)
    await expect(page.getByText('无权访问管理后台')).toBeVisible({ timeout: 15_000 })
    await ctx.close()
  }

  // 管理员体验：直达管理后台（管理员无学员目标，登录后进 /admin）
  {
    const ctx = await browser.newContext()
    const page = await ctx.newPage()
    await page.goto('/login')
    await page.getByRole('button', { name: '管理员体验' }).click()
    await expect(page).toHaveURL(/\/admin$/, { timeout: 15_000 })
    await expect(page.getByRole('heading', { name: '仪表盘' })).toBeVisible()
    await expect(page.getByRole('link', { name: '用户管理' })).toBeVisible()
    await ctx.close()
  }
})
