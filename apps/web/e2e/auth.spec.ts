import { expect, test } from '@playwright/test'
import { LEARNER, uiLogin } from './helpers'

test.describe('登录与鉴权', () => {
  test.beforeAll(async ({ browser }) => {
    // 预热 vite dev server（冷启动依赖优化可能中途整页重载，导致 refresh 请求被中止；
    // 预热后依赖已优化，后续测试稳定）
    const page = await browser.newPage()
    await page.goto('/login')
    await page.waitForTimeout(4000)
    await page.close()
  })

  test('登录成功进入首页', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible()
  })

  test('登录失败显示错误且停留登录页', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('账号').fill(LEARNER.account)
    await page.getByLabel('密码').fill('WrongPassword1')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.locator('.inline-error')).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('未登录访问受保护页面跳转登录；首页对匿名开放（公开 Landing）', async ({ page }) => {
    await page.goto('/profile')
    await expect(page).toHaveURL(/\/login$/)
    // Stage 2.1：打开首页看到的是产品首页，不是登录墙
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '学习，从这里开始' })).toBeVisible()
  })

  test('登出后受保护页面不可访问，公开课程仍可浏览', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await page.getByRole('button', { name: '退出' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '学习，从这里开始' })).toBeVisible()
    await page.goto('/profile')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('access token 失效后自动 refresh，登录状态正常', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    // 破坏 access token（refresh token 保持有效）→ 刷新后应自动 refresh 并保持登录
    await page.evaluate(() => {
      localStorage.setItem('yunsie.accessToken', 'broken-access-token')
    })
    await page.reload()
    await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible({ timeout: 15_000 })
    await expect(page).toHaveURL(/\/$/)
    // access token 已被 refresh 轮换为有效值
    const token = await page.evaluate(() => localStorage.getItem('yunsie.accessToken'))
    expect(token).not.toBe('broken-access-token')
  })

  test('access 与 refresh 都失效时跳转登录页', async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
    await page.evaluate(() => {
      localStorage.setItem('yunsie.accessToken', 'broken-access')
      localStorage.setItem('yunsie.refreshToken', 'broken-refresh')
    })
    await page.reload()
    await expect(page).toHaveURL(/\/login$/)
  })
})
