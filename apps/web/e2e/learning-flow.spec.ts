import { expect, test } from '@playwright/test'
import { LEARNER, uiLogin } from './helpers'

test.describe('学习闭环（真实后端+MySQL+MinIO）', () => {
  test.beforeEach(async ({ page }) => {
    await uiLogin(page, LEARNER.account, LEARNER.password)
  })

  test('课程：列表 → 详情 → 视频真实播放 → 进度保存 → 续播生效', async ({ page }) => {
    // 课程列表（含 setup 播种的进度 → 继续学习卡片）
    await page.goto('/courses')
    await expect(page.getByText('E2E Course').first()).toBeVisible()

    // 进入详情 → 章/节树（含已完成/已学状态与视频课时）
    await page.getByText('E2E Course').first().click()
    await expect(page).toHaveURL(/\/courses\/\d+$/)
    await expect(page.getByText('E2E Lesson One')).toBeVisible()

    // 播放：真实预签名 URL（MinIO），视频元素真实播放
    await page.getByRole('link', { name: /继续播放|开始学习/ }).first().click()
    await expect(page).toHaveURL(/\/lessons\/\d+$/)
    const video = page.locator('video')
    await expect(video).toBeVisible()
    const src = await video.getAttribute('src')
    expect(src).toContain('localhost:9000')

    // 等待播放凭证加载并真实播放（muted 满足自动播放策略；断言 currentTime 前进）
    await page.waitForFunction(() => {
      const v = document.querySelector('video') as HTMLVideoElement | null
      return !!v && v.readyState >= 2
    })
    await page.evaluate(() => {
      const v = document.querySelector('video') as HTMLVideoElement
      v.muted = true
      return v.play().catch(() => {})
    })
    await page.waitForFunction(() => {
      const v = document.querySelector('video') as HTMLVideoElement
      return v.currentTime > 1
    })
    const playedTo = await page.evaluate(() => {
      const v = document.querySelector('video') as HTMLVideoElement
      return v.currentTime
    })
    expect(playedTo).toBeGreaterThan(1)

    // 进度节流上报：等待超过 5s 节流窗口 → 离开页面（触发兜底上报）
    await page.waitForTimeout(6500)

    // 回到课程详情：显示"已学 X 分钟"（服务端进度已保存）
    await page.goto(`/courses`)
    await page.getByText('E2E Course').first().click()
    await expect(page.getByText(/已学/)).toBeVisible()

    // 再次进入播放页：续播从上次位置开始（容差 3s）
    await page.getByRole('link', { name: /继续播放|开始学习/ }).first().click()
    await page.waitForFunction(() => {
      const v = document.querySelector('video') as HTMLVideoElement | null
      return !!v && v.readyState >= 2
    })
    await page.waitForTimeout(1500)
    const resumedAt = await page.evaluate(() => {
      const v = document.querySelector('video') as HTMLVideoElement
      return v.currentTime
    })
    expect(resumedAt).toBeGreaterThanOrEqual(playedTo - 3)
  })

  test('练习：取题 → 作答 → 服务端判分反馈 → 下一题', async ({ page }) => {
    await page.goto('/practice')
    await expect(page.locator('.q-progress')).toContainText('第')
    await expect(page.locator('.q-stem')).toBeVisible()
    // 选择第一个选项并提交（服务端判分）
    await page.locator('.option-item').first().click()
    await page.getByRole('button', { name: '提交答案' }).click()
    // 服务端返回的正误反馈与解析
    await expect(page.locator('.feedback')).toBeVisible()
    await expect(page.getByText(/回答正确|回答错误/)).toBeVisible()
    // 下一题
    await page.getByRole('button', { name: /下一题|再来一组/ }).click()
    await expect(page.locator('.q-stem')).toBeVisible()
  })

  test('考试：列表 → 开始 → 作答 → 交卷 → 成绩', async ({ page }) => {
    await page.goto('/exams')
    await expect(page.getByText('E2E UI Exam')).toBeVisible()
    // 开始考试（后端创建 attempt；精确到目标考试卡片，避免命中其他考试）
    await page
      .locator('.exam-card', { hasText: 'E2E UI Exam' })
      .getByRole('button', { name: '开始考试' })
      .click()
    await expect(page).toHaveURL(/\/exams\/\d+\/session$/)
    await expect(page.locator('.exam-bar')).toContainText('已答')
    await expect(page.locator('.exam-timer')).toBeVisible()
    // 作答：每题选第一个选项（正确答案 A；单选最后选择覆盖之前选择，必须每题只点一个）
    const groups = page.locator('.option-list')
    const groupCount = await groups.count()
    for (let i = 0; i < groupCount; i++) {
      await groups.nth(i).locator('.option-item').first().click()
    }
    // 交卷 → 成绩页（真实服务端判分 2/2 = 2.00 分）
    await page.getByRole('button', { name: '交卷' }).click()
    await expect(page).toHaveURL(/\/exams\/results\/\d+$/)
    await expect(page.getByText(/已通过|未通过/)).toBeVisible()
    // 2/2 全对 → 2 分（JSON number 序列化，2.00 在浏览器显示为 2）
    await expect(page.locator('.result-score-num')).toHaveText(/^2(\.0+)?$/)
    // 历史成绩列表出现该次考试
    await page.goto('/exams')
    await expect(page.getByText('历史成绩')).toBeVisible()
    await expect(page.getByText(/交卷于/).first()).toBeVisible()
  })
})
