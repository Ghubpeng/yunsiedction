import { expect, type Page } from '@playwright/test'

/** E2E 通用登录（真实后端认证链路） */
export async function uiLogin(page: Page, account: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('账号').fill(account)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)
}

export const LEARNER = { account: 'e2e_web_learner', password: 'Learner@1234' }
export const OTHER = { account: 'e2e_web_other', password: 'Other@1234' }
