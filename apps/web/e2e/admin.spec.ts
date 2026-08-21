import { expect, test, type Page } from '@playwright/test'

/**
 * 管理后台 E2E（真实后端 + MySQL + MinIO；数据由 global-setup 创建，teardown 物理清理）。
 * 权限模型：admin=全部；教师=course:* + learn:profile:view + 题目/考试只读；学员=零管理权限。
 */

const ADMIN = { account: 'admin', password: 'Admin@123456' }
const TEACHER = { account: 'e2e_admin_teacher', password: 'Teacher@1234' }
const TEACHER2 = { account: 'e2e_admin_teacher2', password: 'Teacher2@1234' }
const LEARNER = { account: 'e2e_admin_learner', password: 'Learner@1234' }

async function login(page: Page, account: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('账号').fill(account)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)
}

test.describe('管理后台', () => {
  test('管理员登录并进入 /admin（菜单按权限渲染）', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin')
    await expect(page.getByRole('heading', { name: '仪表盘' })).toBeVisible()
    for (const menu of ['用户管理', '角色与权限', '证书与分类', '知识体系', '题库管理', '考试管理', '课程管理', '学员档案']) {
      await expect(page.getByRole('link', { name: menu })).toBeVisible()
    }
    // 仪表盘真实计数
    await expect(page.getByText('内容冷启动清单')).toBeVisible()
  })

  test('非管理员（学员）访问 /admin 被阻断；零管理权限', async ({ page }) => {
    await login(page, LEARNER.account, LEARNER.password)
    await page.goto('/admin')
    await expect(page.getByText('无权访问管理后台')).toBeVisible()
  })

  test('用户管理：列表 + 新建用户', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
    await expect(page.getByText('e2e_web_learner')).toBeVisible()
    await page.getByRole('button', { name: '新建用户' }).click()
    await page.getByLabel('账号').fill('e2e_admin_created')
    await page.getByLabel('昵称').fill('新建测试用户')
    await page.getByLabel('初始密码').fill('Created@1234')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('用户已创建')).toBeVisible()
    await expect(page.getByText('e2e_admin_created')).toBeVisible()
  })

  test('角色与权限展示', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/rbac')
    await expect(page.getByText('e2e_admin_teacher_role')).toBeVisible()
    await page.getByRole('tab', { name: '权限点' }).click()
    await expect(page.getByText('course:course:publish')).toBeVisible()
    await expect(page.getByText('*:*:*')).toBeVisible()
  })

  test('证书 CRUD（分类树 + 证书）', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/certificates')
    await expect(page.locator('.tree-list').getByText('E2E Cat')).toBeVisible()
    // 新建分类
    await page.getByRole('button', { name: '新建根分类' }).click()
    await page.getByLabel('分类名称').fill('E2E Admin Cat')
    await page.getByLabel('分类编码').fill('cat-admin-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('分类已创建')).toBeVisible()
    await expect(page.getByText('E2E Admin Cat')).toBeVisible()
    // 选中分类 → 新建证书
    await page.getByText('E2E Admin Cat').click()
    await page.getByRole('button', { name: '新建证书' }).click()
    await page.getByLabel('证书名称').fill('E2E Admin Cert')
    await page.getByLabel('证书编码').fill('cert-admin-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('证书已创建')).toBeVisible()
    await expect(page.getByText('E2E Admin Cert')).toBeVisible()
  })

  test('知识体系：版本与节点管理', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/subjects')
    // dev 库并存 Demo 证书（排在默认位）：显式选择 E2E Cert 再断言其节点
    await page.getByLabel('选择证书').selectOption({ label: 'E2E Cert' })
    await expect(page.getByText('E2E KP')).toBeVisible()
    // 新建章节节点（dev 库并存多个科目，取第一个「加章节」）
    await page.getByRole('button', { name: '加章节' }).first().click()
    await page.getByLabel('名称').fill('E2E Admin Chapter')
    await page.getByLabel('编码').fill('ch-admin-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('节点已创建')).toBeVisible()
    await expect(page.getByText('E2E Admin Chapter')).toBeVisible()
  })

  test('题目 CRUD 与审核（提审 → 通过 = 唯一发布通道）', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/questions')
    // dev 库并存 Demo 证书：显式选择 E2E Cert（关联知识点列表按证书过滤）
    await page.getByLabel('选择证书').selectOption({ label: 'E2E Cert' })
    // 创建（关联知识点）
    await page.getByRole('button', { name: '新建题目' }).click()
    await page.getByLabel('题干').fill('E2E Admin Q1')
    await page.getByLabel('答案').fill('A')
    await page.getByPlaceholder('选项 A').fill('OptA')
    await page.getByPlaceholder('选项 B').fill('OptB')
    await page.getByLabel('解析').fill('A only')
    await page.getByText('E2E KP', { exact: false }).first().click()
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('题目已创建')).toBeVisible()
    // 提审 → 待审核；通过 → 已发布
    const row = page.locator('tr', { hasText: 'E2E Admin Q1' })
    await expect(row.getByText('草稿')).toBeVisible()
    await row.getByRole('button', { name: '提审' }).click()
    await expect(row.getByText('待审核')).toBeVisible()
    await row.getByRole('button', { name: '通过' }).click()
    await expect(row.getByText('已发布')).toBeVisible()
  })

  test('Excel 导入（真实 xlsx + 行级反馈）', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/questions/import')
    // dev 库并存 Demo 证书：显式选择 E2E Cert（fixture 的知识点编码属于该证书）
    await page.getByLabel('所属证书').selectOption({ label: 'E2E Cert' })
    await page.locator('input[type=file]').setInputFiles('e2e/fixtures/questions.xlsx')
    await page.getByRole('button', { name: '开始导入' }).click()
    await expect(page.getByText(/成功 1 行/)).toBeVisible()
    await expect(page.getByText(/全部入库/)).toBeVisible()
  })

  test('考试：创建 → 组卷 → 发布', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/exams')
    await page.getByRole('button', { name: '新建考试' }).click()
    // dev 库并存 Demo 证书：显式选择 E2E Cert（其下有 setup 发布的题目，组卷配额充足）
    await page.locator('select[name="certificateId"]').selectOption({ label: 'E2E Cert' })
    await page.getByLabel('考试名称').fill('E2E Admin Exam1')
    await page.getByLabel('题目总数').fill('2')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('考试已创建')).toBeVisible()
    const row = page.locator('tr', { hasText: 'E2E Admin Exam1' })
    await expect(row.getByText('草稿')).toBeVisible()
    await row.getByRole('button', { name: '组卷' }).click()
    await expect(page.getByText('组卷成功')).toBeVisible()
    await row.getByRole('button', { name: '发布' }).click()
    await expect(page.getByText('已发布').first()).toBeVisible()
    await expect(row.locator('.badge', { hasText: '已发布' })).toBeVisible()
  })

  test('成绩查看（只读）', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    await page.goto('/admin/exams')
    await page.locator('tr', { hasText: 'E2E Seed Exam' }).getByRole('link', { name: /E2E Seed Exam/ }).click()
    await page.getByRole('tab', { name: '成绩' }).click()
    await expect(page.getByText('成绩（只读）')).toBeVisible()
    await expect(page.locator('.admin-table tbody tr').first()).toBeVisible()
  })

  test('教师 DataScope：仅见自己课程；课程内容管理 + 视频上传 + 发布', async ({ page }) => {
    await login(page, TEACHER.account, TEACHER.password)
    await page.goto('/admin')
    // 教师菜单只有课程/学员等（无用户管理）
    await expect(page.getByRole('link', { name: '课程管理' })).toBeVisible()
    await expect(page.getByRole('link', { name: '用户管理' })).toHaveCount(0)
    await page.goto('/admin/courses')
    // DataScope：只看到课程 A（setup 教师A名下），看不到课程 B
    await expect(page.getByText('E2E Admin Course A')).toBeVisible()
    await expect(page.getByText('E2E Admin Course B')).toHaveCount(0)
    // 内容管理：加章节 + 小节 + 上传视频 + 发布
    await page.getByRole('link', { name: '内容管理' }).click()
    await page.getByRole('button', { name: '新建章节' }).click()
    await page.getByLabel('章节标题').fill('Teacher Chapter')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('章节已创建')).toBeVisible()
    await page.locator('.card', { hasText: 'Teacher Chapter' }).getByRole('button', { name: '新建小节' }).click()
    await page.getByLabel('小节标题').fill('Teacher Lesson')
    await page.getByLabel('时长（秒）').fill('30')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('小节已创建')).toBeVisible()
    // 视频上传（真实 MinIO）
    await page.locator('.list-row', { hasText: 'Teacher Lesson' }).getByRole('button', { name: '上传视频' }).click()
    await page.locator('.modal-panel input[type=file]').setInputFiles('e2e/fixtures/flower.mp4')
    await page.getByRole('button', { name: '确认上传' }).click()
    await expect(page.getByText('视频已上传')).toBeVisible()
    // 发布（状态徽标变为已发布）
    await page.getByRole('button', { name: '发布课程' }).click()
    await expect(page.locator('.badge', { hasText: '已发布' })).toBeVisible()
  })

  test('教师 DataScope 隔离：教师B 看不到教师A 的课程与学员', async ({ page }) => {
    await login(page, TEACHER2.account, TEACHER2.password)
    await page.goto('/admin/courses')
    await expect(page.getByText('E2E Admin Course B')).toBeVisible()
    await expect(page.getByText('E2E Admin Course A')).toHaveCount(0)
    await page.goto('/admin/students')
    // 教师B 的课程列表不含课程 A；选课程 B → 无学员
    await expect(page.getByText('该课程暂无学员学习记录')).toBeVisible()
  })

  test('学员档案查看（教师经 DataScope）', async ({ page }) => {
    await login(page, TEACHER.account, TEACHER.password)
    await page.goto('/admin/students')
    await page.locator('select[aria-label="选择课程"]').selectOption({ label: 'E2E Admin Course A' })
    await expect(page.getByText('E2E 管理学员')).toBeVisible()
    await page.getByRole('button', { name: '查看档案' }).click()
    await expect(page.getByText('档案详情')).toBeVisible()
    await expect(page.getByText('掌握度 / 薄弱点')).toBeVisible()
  })

  test('403 不白屏：教师访问无权限页面显示内联错误', async ({ page }) => {
    await login(page, TEACHER.account, TEACHER.password)
    await page.goto('/admin/users')
    await expect(page.locator('.inline-error')).toBeVisible()
    // 未知路由 → 回用户端首页
    await page.goto('/admin/no-such-page')
    await expect(page).toHaveURL(/\/$/)
  })

  test('业务错误不白屏：无内容发布 → 服务端 30505 提示', async ({ page }) => {
    await login(page, TEACHER2.account, TEACHER2.password)
    await page.goto('/admin/courses')
    await page.locator('tr', { hasText: 'E2E Admin Course B' }).getByRole('button', { name: '发布' }).click()
    await expect(page.getByText(/发布前必须存在至少 1 个启用章节/)).toBeVisible()
  })

  test('traceId header 与响应 body 一致', async ({ page }) => {
    await login(page, ADMIN.account, ADMIN.password)
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/course/courses') && r.request().method() === 'GET'),
      page.goto('/admin/courses'),
    ])
    const headerTrace = response.headers()['x-trace-id']
    const body = (await response.json()) as { traceId: string }
    expect(headerTrace).toBeTruthy()
    expect(headerTrace).toBe(body.traceId)
  })
})
