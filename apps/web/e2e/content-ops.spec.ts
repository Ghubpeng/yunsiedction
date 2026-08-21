import { expect, test, type Page } from '@playwright/test'

/**
 * Stage 2.3B 内容运营中心 E2E：
 * 内容复制（证书体系：科目/章节/知识点/课程结构/可选题库）、
 * AI 学习助手规则推荐展示（无真实 LLM，诚实标注）、Dashboard 运营提醒。
 * 创建证书/科目/章节/课程绑定与删除恢复题目已由 content-lifecycle.spec.ts 覆盖。
 * 数据自包含（CO 前缀），teardown 按名称模式物理清理。
 */

const ADMIN = { account: 'admin', password: 'Admin@123456' }

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('账号').fill(ADMIN.account)
  await page.getByLabel('密码').fill(ADMIN.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)
}

test.describe('内容运营中心（Stage 2.3B）', () => {
  test('1. 内容复制：复制证书体系（科目/章节/知识点/课程结构/可选题库）', async ({ page }) => {
    await login(page)
    // ---- 源证书体系（CO 前缀，自包含） ----
    await page.goto('/admin/certificates')
    await page.getByRole('button', { name: '新建根分类' }).click()
    await page.getByLabel('分类名称').fill('CO Cat')
    await page.getByLabel('分类编码').fill('cat-co-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('分类已创建')).toBeVisible()
    await page.getByText('CO Cat').click()
    await page.getByRole('button', { name: '新建证书' }).click()
    await page.getByLabel('证书名称').fill('CO Cert')
    await page.getByLabel('证书编码').fill('cert-co-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('证书已创建')).toBeVisible()

    // 版本 + 科目 + 章节 + 知识点
    await page.goto('/admin/subjects')
    await page.getByLabel('选择证书').selectOption({ label: 'CO Cert' })
    await page.getByRole('button', { name: '新建版本' }).click()
    await page.getByLabel('版本号').fill('2026')
    await page.getByLabel('版本名称').fill('2026版')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('版本已创建')).toBeVisible()
    await page.getByRole('button', { name: '新建科目' }).click()
    await page.getByLabel('科目名称').fill('CO Subject')
    await page.getByLabel('科目编码').fill('subj-co-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('科目已创建')).toBeVisible()
    await page.locator('li.tree-row', { hasText: 'CO Subject' }).getByRole('button', { name: '加章节' }).click()
    await page.getByLabel('名称').fill('CO Chapter')
    await page.getByLabel('编码').fill('ch-co-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('节点已创建')).toBeVisible()
    await page.locator('li.tree-row', { hasText: 'CO Chapter' }).getByRole('button', { name: '加知识点' }).click()
    await page.getByLabel('名称').fill('CO KP')
    await page.getByLabel('编码').fill('kp-co-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('节点已创建')).toBeVisible()

    // 已发布题目
    await page.goto('/admin/questions')
    await page.getByLabel('选择证书').selectOption({ label: 'CO Cert' })
    await page.getByRole('button', { name: '新建题目' }).click()
    await page.getByLabel('题干').fill('CO Q1')
    await page.getByLabel('答案').fill('A')
    await page.getByPlaceholder('选项 A').fill('OptA')
    await page.getByPlaceholder('选项 B').fill('OptB')
    await page.getByLabel('解析').fill('A only')
    await page.getByText('CO KP', { exact: false }).first().click()
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('题目已创建')).toBeVisible()
    const qRow = page.locator('tr', { hasText: 'CO Q1' })
    await qRow.getByRole('button', { name: '提审' }).click()
    await expect(qRow.getByText('待审核')).toBeVisible()
    await qRow.getByRole('button', { name: '通过' }).click()
    await expect(qRow.getByText('已发布')).toBeVisible()

    // 课程（绑定章节）
    await page.goto('/admin/courses')
    await page.getByRole('button', { name: '新建课程' }).click()
    await page.locator('select[name="certificateId"]').selectOption({ label: 'CO Cert' })
    await expect(page.locator('select[name="versionId"]')).toBeEnabled()
    await page.locator('select[name="subjectId"]').selectOption({ label: 'CO Subject' })
    await expect(page.locator('select[name="chapterId"]')).toBeEnabled()
    await page.locator('select[name="chapterId"]').selectOption({ label: 'CO Chapter' })
    await page.locator('select[name="teacherId"]').selectOption({ index: 0 })
    await page.locator('input[name="title"]').fill('CO Course')
    await page.locator('textarea[name="description"]').fill('复制源课程')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('课程已创建')).toBeVisible()

    // ---- 复制证书体系 ----
    await page.goto('/admin/certificates')
    await page.getByRole('button', { name: /CO Cat/ }).click()
    await page.locator('tr', { hasText: 'CO Cert' }).getByRole('button', { name: '复制体系' }).click()
    await page.getByLabel('目标证书编码').fill('cert-co-copy')
    await page.getByRole('button', { name: '开始复制' }).click()
    await expect(page.getByText(/证书体系已复制：1 科目 · 2 节点 · 1 课程 · 1 题/)).toBeVisible()
    await expect(page.getByText('CO Cert（副本）')).toBeVisible()

    // ---- 验证副本知识体系/课程/题目 ----
    await page.goto('/admin/subjects')
    await page.getByLabel('选择证书').selectOption({ label: 'CO Cert（副本）' })
    await expect(page.locator('li.tree-row', { hasText: 'CO Subject' })).toBeVisible()
    await expect(page.getByText('CO Chapter')).toBeVisible()
    await page.goto('/admin/courses')
    // 源 + 副本共 2 门同名课程（副本课程结构已复制）
    await expect(page.locator('tr', { hasText: 'CO Course' })).toHaveCount(2)
    await expect(page.locator('tr', { hasText: 'CO Course' }).getByText(/CO Subject \/ CO Chapter/).first()).toBeVisible()
    await page.goto('/admin/questions')
    await page.getByLabel('选择证书').selectOption({ label: 'CO Cert（副本）' })
    const copyRow = page.locator('tr', { hasText: 'CO Q1' })
    await expect(copyRow.getByText('草稿')).toBeVisible()
  })

  test('2. AI 学习助手规则推荐展示（基于真实数据，非 AI 生成）', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('账号').fill('e2e_web_learner')
    await page.getByLabel('密码').fill('Learner@1234')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/$/)
    // 学习助手面板（global-setup 已为学员播种错题/课程/掌握度）
    await page.getByRole('button', { name: /学习助手/ }).click()
    await expect(page.getByText('今日学习建议（规则推荐）')).toBeVisible()
    await expect(page.getByText(/非 AI 生成/)).toBeVisible()
    await expect(page.getByText(/薄弱知识点|推荐课程|推荐练习/).first()).toBeVisible()
    await expect(page.getByText('学习下一步')).toBeVisible()
  })

  test('3. Dashboard 运营提醒（待审核题目/空章节/无课程章节）', async ({ page }) => {
    await login(page)
    await page.goto('/admin')
    await expect(page.getByRole('heading', { name: '仪表盘' })).toBeVisible()
    await expect(page.getByText('运营提醒')).toBeVisible()
    await expect(page.getByText(/待审核题目：\d+ 道/)).toBeVisible()
    await expect(page.getByRole('link', { name: /视频/ })).toBeVisible()
  })
})
