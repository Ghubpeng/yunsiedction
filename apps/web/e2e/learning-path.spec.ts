import { expect, test, type Page } from '@playwright/test'

/**
 * Stage 2.4 用户学习路径产品化 E2E：
 * 学习路径展示 / 章节学习状态（未开始→学习中→已完成→掌握） / 章节练习与掌握反馈 /
 * 下一任务推荐 / 周学习报告 / AI 助手固定入口。
 * 「首次选择考试」已由 learning-product.spec.ts（首次进入：无目标只选证书 → 一键开始准备）覆盖。
 * 数据基于 global-setup 的 e2e_web_learner（目标=E2E Cert，课程 1 章 2 节，章节关联知识点+2 道已发布题）。
 */

const LEARNER = { account: 'e2e_web_learner', password: 'Learner@1234' }

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('账号').fill(LEARNER.account)
  await page.getByLabel('密码').fill(LEARNER.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/$/)
}

/** 经真实 API 上报课时进度（与播放器同链路；视频完成判定由服务端计算） */
async function reportLesson(page: Page, lessonId: number, positionSeconds: number): Promise<void> {
  const token = await page.evaluate(() => localStorage.getItem('yunsie.accessToken'))
  const resp = await page.request.post('/api/v1/course/progress', {
    headers: { Authorization: `Bearer ${token}` },
    data: { lessonId, positionSeconds },
  })
  expect(resp.ok()).toBeTruthy()
}

/** 读取当前课程树（课时/章节 id 由服务端数据驱动） */
async function treeOf(page: Page, courseId: number): Promise<{
  chapterId: number
  lessons: { id: number; finished: number; positionSeconds: number }[]
}> {
  const token = await page.evaluate(() => localStorage.getItem('yunsie.accessToken'))
  const resp = await page.request.get(`/api/v1/course/public/courses/${courseId}/tree`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(resp.ok()).toBeTruthy()
  const body = (await resp.json()) as { data: { chapters: { id: number; lessons: { id: number; finished: number; positionSeconds: number }[] }[] } }
  return { chapterId: body.data.chapters[0].id, lessons: body.data.chapters[0].lessons }
}

test.describe('用户学习路径（Stage 2.4）', () => {
  test('1. 学习路径展示：当前考试/总完成度/学习阶段/下一任务/课程树', async ({ page }) => {
    await login(page)
    await page.goto('/learn-path')
    await expect(page.getByText('学习路径', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('E2E Cert', { exact: false })).toBeVisible()
    await expect(page.getByText('总完成度')).toBeVisible()
    await expect(page.getByText(/起步|基础|强化|冲刺/)).toBeVisible()
    await expect(page.getByText('下一学习任务')).toBeVisible()
    await expect(page.getByText('E2E Course', { exact: false }).first()).toBeVisible()
    // 章节四态徽标之一（setup 已在 lesson1 报 5s → 学习中）
    await expect(page.getByText(/未开始|学习中|已完成|掌握/).first()).toBeVisible()
  })

  test('2. 章节学习状态：学习中 → 已完成（视频完成判定由服务端计算）', async ({ page }) => {
    await login(page)
    // setup 已给 lesson1 报 5s 进度 → 章节「学习中」
    await page.goto('/learn-path')
    await expect(page.getByText('学习中').first()).toBeVisible()
    // 完成全部课时（30 秒视频 → finished）
    const token = await page.evaluate(() => localStorage.getItem('yunsie.accessToken'))
    const myCourses = (await (await page.request.get('/api/v1/course/my-courses', { headers: { Authorization: `Bearer ${token}` } })).json()) as {
      data: { courseId: number; lastLessonId: number }[]
    }
    const courseId = myCourses.data[0].courseId
    const tree = await treeOf(page, courseId)
    for (const lesson of tree.lessons) {
      await reportLesson(page, lesson.id, 30)
    }
    await page.goto(`/courses/${courseId}`)
    await expect(page.getByText('已完成').first()).toBeVisible()
    await expect(page.getByRole('link', { name: '开始章节练习' })).toBeVisible()
  })

  test('3. 下一任务推荐：首页今日任务（视频完成 → 章节练习任务）', async ({ page }) => {
    await login(page)
    await expect(page.getByText('今日任务')).toBeVisible()
    await expect(page.getByText(/完成《E2E Course》.*章节练习/)).toBeVisible()
  })

  test('4. 章节练习与掌握反馈：得分/掌握度/薄弱点/下一步 → 章节掌握', async ({ page }) => {
    await login(page)
    const token = await page.evaluate(() => localStorage.getItem('yunsie.accessToken'))
    const myCourses = (await (await page.request.get('/api/v1/course/my-courses', { headers: { Authorization: `Bearer ${token}` } })).json()) as {
      data: { courseId: number }[]
    }
    const courseId = myCourses.data[0].courseId
    const tree = await treeOf(page, courseId)
    const chapterId = tree.chapterId

    await page.goto(`/courses/${courseId}/chapters/${chapterId}/practice`)
    await expect(page.locator('.option-item').first()).toBeVisible()
    // 逐题作答（判分由服务端；选项随便点，对错都由后端裁决），直到出现总结页
    let safety = 0
    while (safety < 20) {
      safety++
      if ((await page.getByText(/本章练习 \d+\/\d+ 正确 · 得分 \d+/).count()) > 0) break
      const option = page.locator('.option-item').first()
      if ((await option.count()) === 0) break
      await option.click()
      await page.getByRole('button', { name: '提交答案' }).click()
      await expect(page.getByText(/回答正确|回答错误/)).toBeVisible()
      const nextBtn = page.getByRole('button', { name: /下一题|查看本章反馈/ })
      await nextBtn.click()
    }
    // 完成反馈：得分 + 掌握度/薄弱点 + 下一步建议（规则推荐，非 AI）
    await expect(page.getByText(/本章练习 \d+\/\d+ 正确 · 得分 \d+/)).toBeVisible()
    await expect(page.getByText(/掌握度与薄弱点|下一步建议/).first()).toBeVisible()
    await expect(page.getByText(/非 AI 生成/)).toBeVisible()
    await page.getByRole('link', { name: /返回课程/ }).click()
    // 章节状态 → 掌握（视频完成 + 章节练习完成）
    await expect(page.getByText('掌握').first()).toBeVisible()
  })

  test('5. 周学习报告：学习时间/完成章节/练习数量/掌握变化', async ({ page }) => {
    await login(page)
    await page.goto('/profile')
    await expect(page.getByText('本周学习报告')).toBeVisible()
    const report = page.getByLabel('周学习报告')
    await expect(report.getByText('学习时间')).toBeVisible()
    await expect(report.getByText('完成章节')).toBeVisible()
    await expect(report.getByText('练习', { exact: true })).toBeVisible()
    await expect(report.getByText('掌握变化')).toBeVisible()
  })

  test('6. AI 学习助手固定入口：练习页 + 档案页（rule-based，非 LLM）', async ({ page }) => {
    await login(page)
    // 练习页入口
    await page.goto('/practice')
    await page.getByRole('button', { name: /学习助手/ }).click()
    await expect(page.getByText('今日学习建议（规则推荐）')).toBeVisible()
    await expect(page.getByText(/非 AI 生成/).first()).toBeVisible()
    await expect(page.getByText('学习下一步').first()).toBeVisible()
    // 档案页入口
    await page.goto('/profile')
    await page.getByRole('button', { name: /学习助手/ }).click()
    await expect(page.getByText('今日学习建议（规则推荐）')).toBeVisible()
  })
})
