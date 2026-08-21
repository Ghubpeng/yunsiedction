import { expect, test } from '@playwright/test'
import { LEARNER, OTHER, uiLogin } from './helpers'

test.describe('首页 / 档案 / 消息 / 隔离 / 错误处理', () => {
  test('首页为学习入口（当前考试/今日学习/最近学习/未读徽标）', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible()
    await expect(page.getByText(/当前考试：E2E Cert/)).toBeVisible()
    await expect(page.getByText('今日学习')).toBeVisible()
    await expect(page.getByText('E2E Course').first()).toBeVisible()
    await expect(page.getByText(/最近学习/)).toBeVisible()
    // 未读消息徽标（setup 交卷产生的成绩通知）
    await expect(page.locator('.unread-dot').first()).toBeVisible()
  })

  test('学习档案：summary/掌握度/薄弱点/日历/预测（标注参考）', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await page.goto('/profile')
    await expect(page.getByText('学习概览')).toBeVisible()
    await expect(page.getByText('知识点掌握度')).toBeVisible()
    await expect(page.getByText('E2E KP').first()).toBeVisible()
    await expect(page.getByText('薄弱点')).toBeVisible()
    await expect(page.getByText(/学习日历/)).toBeVisible()
    // 预测必须标注参考性（无承诺文案）
    await expect(page.getByText('考试通过率预测')).toBeVisible()
    await expect(page.getByText(/参考性估算/)).toBeVisible()
  })

  test('站内消息：列表/未读/单条已读/全部已读/删除/空状态', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await page.goto('/messages')
    // setup 交卷产生的成绩通知
    await expect(page.getByText('成绩通知', { exact: true })).toBeVisible()
    await expect(page.getByText('E2E Seed Exam')).toBeVisible()
    // 单条已读 → 未读数下降
    await page.getByRole('button', { name: '标记已读' }).first().click()
    await expect(page.getByText(/未读 0 条/)).toBeVisible()
    // 删除 → 空状态
    await page.getByRole('button', { name: '删除' }).click()
    await expect(page.getByText('暂无消息')).toBeVisible()
    // 顶部徽标消失
    await expect(page.locator('.unread-dot')).toHaveCount(0)
  })

  test('用户隔离：B 用户看不到 A 的任何数据', async ({ page }) => {
    await uiLogin(page, OTHER.account, OTHER.password)
    // 首页：B 无课程（无 A 的数据泄漏），展示空态
    await expect(page.getByText('还没有开始学习')).toBeVisible()
    // 消息：空状态
    await page.goto('/messages')
    await expect(page.getByText('暂无消息')).toBeVisible()
    // 档案：无练习数据
    await page.goto('/profile')
    await expect(page.getByText('暂无掌握度数据')).toBeVisible()
  })

  test('业务错误不白屏：不存在课程显示内联错误', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await page.goto('/courses/999999')
    await expect(page.locator('.inline-error')).toBeVisible()
  })

  test('无视频课时显示友好错误（30512）', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await page.goto('/courses')
    await page.getByText('E2E Course').first().click()
    await expect(page.getByText('E2E Lesson Two')).toBeVisible()
    // 小节2 无视频 → 播放页友好错误，无白屏
    await page.locator('.list-row', { hasText: 'E2E Lesson Two' }).getByRole('link').click()
    await expect(page).toHaveURL(/\/lessons\/\d+$/)
    await expect(page.locator('.inline-error')).toBeVisible()
  })
})
