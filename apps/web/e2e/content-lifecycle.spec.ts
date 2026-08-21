import { expect, test, type Page } from '@playwright/test'

/**
 * Stage 2.3A 内容管理生命周期 E2E（9 项）：
 * 创建证书/版本/科目/章节/课程（四层级归属）、删除草稿题、发布题无法删除、
 * 下架题（后可删除）、科目停用。
 * 数据自包含（LC 前缀），teardown 按名称模式物理清理。
 */

const ADMIN = { account: 'admin', password: 'Admin@123456' }

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('账号').fill(ADMIN.account)
  await page.getByLabel('密码').fill(ADMIN.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)
}

/** 题库页：筛选 LC 证书并创建一道草稿题（关联 LC KP 知识点） */
async function createDraftQuestion(page: Page, stem: string): Promise<void> {
  await page.getByLabel('选择证书').selectOption({ label: 'LC Cert' })
  await page.getByRole('button', { name: '新建题目' }).click()
  await page.getByLabel('题干').fill(stem)
  await page.getByLabel('答案').fill('A')
  await page.getByPlaceholder('选项 A').fill('OptA')
  await page.getByPlaceholder('选项 B').fill('OptB')
  await page.getByLabel('解析').fill('A only')
  await page.getByText('LC KP', { exact: false }).first().click()
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('题目已创建')).toBeVisible()
}

test.describe('内容管理生命周期（Stage 2.3A）', () => {
  test('1. 创建证书（分类 + 证书）', async ({ page }) => {
    await login(page)
    await page.goto('/admin/certificates')
    await page.getByRole('button', { name: '新建根分类' }).click()
    await page.getByLabel('分类名称').fill('LC Cat')
    await page.getByLabel('分类编码').fill('cat-lc-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('分类已创建')).toBeVisible()
    await page.getByText('LC Cat').click()
    await page.getByRole('button', { name: '新建证书' }).click()
    await page.getByLabel('证书名称').fill('LC Cert')
    await page.getByLabel('证书编码').fill('cert-lc-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('证书已创建')).toBeVisible()
    await expect(page.getByText('LC Cert')).toBeVisible()
  })

  test('2. 创建知识版本', async ({ page }) => {
    await login(page)
    await page.goto('/admin/subjects')
    await page.getByLabel('选择证书').selectOption({ label: 'LC Cert' })
    await page.getByRole('button', { name: '新建版本' }).click()
    await page.getByLabel('版本号').fill('2026')
    await page.getByLabel('版本名称').fill('2026版')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('版本已创建')).toBeVisible()
    // 新建版本后自动选中（列表首个/当前版本）；option 元素无可见性语义，断言选中项
    await expect(page.getByLabel('选择版本').locator('option:checked')).toHaveText(/2026/)
  })

  test('3. 创建考试科目', async ({ page }) => {
    await login(page)
    await page.goto('/admin/subjects')
    await page.getByLabel('选择证书').selectOption({ label: 'LC Cert' })
    await page.getByRole('button', { name: '新建科目' }).click()
    await page.getByLabel('科目名称').fill('LC Subject')
    await page.getByLabel('科目编码').fill('subj-lc-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('科目已创建')).toBeVisible()
    await expect(page.locator('li.tree-row', { hasText: 'LC Subject' })).toBeVisible()
  })

  test('4. 创建章节（含知识点，供题目关联）', async ({ page }) => {
    await login(page)
    await page.goto('/admin/subjects')
    await page.getByLabel('选择证书').selectOption({ label: 'LC Cert' })
    // 章节（node_type=1，带描述/排序）
    await page.locator('li.tree-row', { hasText: 'LC Subject' }).getByRole('button', { name: '加章节' }).click()
    await page.getByLabel('名称').fill('LC Chapter')
    await page.getByLabel('描述').fill('生命周期章节')
    await page.getByLabel('编码').fill('ch-lc-e2e')
    await page.getByLabel('排序').fill('1')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('节点已创建')).toBeVisible()
    await expect(page.getByText('LC Chapter')).toBeVisible()
    // 知识点（题目关联用）
    await page.locator('li.tree-row', { hasText: 'LC Chapter' }).getByRole('button', { name: '加知识点' }).click()
    await page.getByLabel('名称').fill('LC KP')
    await page.getByLabel('编码').fill('kp-lc-e2e')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('节点已创建')).toBeVisible()
    await expect(page.getByText('LC KP')).toBeVisible()
  })

  test('5. 创建课程（证书 → 版本 → 科目 → 章节 四层级必选）', async ({ page }) => {
    await login(page)
    await page.goto('/admin/courses')
    await page.getByRole('button', { name: '新建课程' }).click()
    await page.locator('select[name="certificateId"]').selectOption({ label: 'LC Cert' })
    await expect(page.locator('select[name="versionId"]')).toBeEnabled()
    await page.locator('select[name="subjectId"]').selectOption({ label: 'LC Subject' })
    await expect(page.locator('select[name="chapterId"]')).toBeEnabled()
    await page.locator('select[name="chapterId"]').selectOption({ label: 'LC Chapter' })
    await page.locator('select[name="teacherId"]').selectOption({ index: 0 })
    await page.locator('input[name="title"]').fill('LC Course')
    await page.locator('textarea[name="description"]').fill('生命周期课程')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.getByText('课程已创建')).toBeVisible()
    const row = page.locator('tr', { hasText: 'LC Course' })
    await expect(row.getByText(/LC Subject \/ LC Chapter/)).toBeVisible()
  })

  test('6. 删除草稿题（草稿可删除）', async ({ page }) => {
    await login(page)
    await page.goto('/admin/questions')
    await createDraftQuestion(page, 'LC Q1')
    const row = page.locator('tr', { hasText: 'LC Q1' })
    await expect(row.getByText('草稿')).toBeVisible()
    await row.getByRole('button', { name: '删除' }).click()
    await page.getByRole('button', { name: '确认删除' }).click()
    await expect(page.getByText('已删除')).toBeVisible()
    await expect(page.getByText('LC Q1')).toHaveCount(0)
  })

  test('7. 已发布题删除进入回收站（历史受保护，可恢复为草稿）', async ({ page }) => {
    await login(page)
    await page.goto('/admin/questions')
    await createDraftQuestion(page, 'LC Q2')
    const row = page.locator('tr', { hasText: 'LC Q2' })
    await row.getByRole('button', { name: '提审' }).click()
    await expect(row.getByText('待审核')).toBeVisible()
    await row.getByRole('button', { name: '通过' }).click()
    await expect(row.getByText('已发布')).toBeVisible()
    await row.getByRole('button', { name: '删除' }).click()
    await page.getByRole('button', { name: '确认删除' }).click()
    await expect(page.getByText('已移入回收站')).toBeVisible()
    await expect(page.getByText('LC Q2')).toHaveCount(0)
    // 回收站可见并可恢复 → 草稿
    await page.getByLabel('题目状态').selectOption('6')
    const recycledRow = page.locator('tr', { hasText: 'LC Q2' })
    await expect(recycledRow.getByText('回收站')).toBeVisible()
    await recycledRow.getByRole('button', { name: '恢复' }).click()
    await expect(page.getByText('已恢复为草稿')).toBeVisible()
    await page.getByLabel('题目状态').selectOption('1')
    await expect(page.locator('tr', { hasText: 'LC Q2' }).getByText('草稿')).toBeVisible()
  })

  test('8. 下架题（下架后可删除）', async ({ page }) => {
    await login(page)
    await page.goto('/admin/questions')
    await createDraftQuestion(page, 'LC Q3')
    const row = page.locator('tr', { hasText: 'LC Q3' })
    await row.getByRole('button', { name: '提审' }).click()
    await expect(row.getByText('待审核')).toBeVisible()
    await row.getByRole('button', { name: '通过' }).click()
    await expect(row.getByText('已发布')).toBeVisible()
    await row.getByRole('button', { name: '下架' }).click()
    await expect(row.getByText('已下架')).toBeVisible()
    await row.getByRole('button', { name: '删除' }).click()
    await page.getByRole('button', { name: '确认删除' }).click()
    await expect(page.getByText('已删除')).toBeVisible()
    await expect(page.getByText('LC Q3')).toHaveCount(0)
  })

  test('9. 科目停用/启用', async ({ page }) => {
    await login(page)
    await page.goto('/admin/subjects')
    await page.getByLabel('选择证书').selectOption({ label: 'LC Cert' })
    const row = page.locator('li.tree-row', { hasText: 'LC Subject' })
    await row.getByRole('button', { name: '停用' }).click()
    await expect(page.getByText('科目已停用')).toBeVisible()
    await expect(row.locator('.badge', { hasText: '停用' })).toBeVisible()
    await row.getByRole('button', { name: '启用' }).click()
    await expect(page.getByText('科目已启用')).toBeVisible()
    await expect(row.locator('.badge', { hasText: '启用' })).toBeVisible()
  })
})
