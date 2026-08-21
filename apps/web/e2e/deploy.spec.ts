import { expect, test, type APIRequestContext } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * Stage 2.0 生产部署冒烟（15 项）：目标为 compose 交付栈（nginx:5173 → server:8080）。
 * 覆盖：SPA 静态服务语义（入口不缓存/深链回退/未知路由/资源缓存/资源 404）、
 * 反向代理语义（JSON 透传/404 不回退/登录全链路）、业务链路（课程/上传/播放凭证 Host）、
 * 依赖探活（actuator/minio）与浏览器登录。
 * 前置：scripts\up.ps1 完成；数据由 deploy-setup 创建（经 nginx，不直连 8080）。
 */

const __dirname = dirname(fileURLToPath(import.meta.url))

interface DeployState {
  learnerAccount: string
  learnerPassword: string
  courseId: number
  lessonId: number
}
const state = JSON.parse(readFileSync(join(__dirname, '.deploy-state.json'), 'utf-8')) as DeployState

async function loginViaProxy(request: APIRequestContext, account: string, password: string): Promise<string> {
  const resp = await request.post('/api/v1/user/auth/login', { data: { account, password } })
  const json = await resp.json()
  expect(json.code, json.message).toBe(0)
  return json.data.accessToken as string
}

test('1. 首页返回 SPA 入口且 index 不缓存', async ({ request }) => {
  const resp = await request.get('/')
  expect(resp.status()).toBe(200)
  expect(resp.headers()['content-type'] ?? '').toContain('text/html')
  const html = await resp.text()
  expect(html).toContain('id="root"')
  expect((resp.headers()['cache-control'] ?? '').toLowerCase()).toContain('no-cache')
})

test('2. 前端路由深链回退 index.html（/admin）', async ({ request }) => {
  const resp = await request.get('/admin')
  expect(resp.status()).toBe(200)
  expect(resp.headers()['content-type'] ?? '').toContain('text/html')
  expect(await resp.text()).toContain('id="root"')
})

test('3. 未知前端路由回退 index.html 而非 404', async ({ request }) => {
  const resp = await request.get('/no-such-frontend-route')
  expect(resp.status()).toBe(200)
  expect(resp.headers()['content-type'] ?? '').toContain('text/html')
  expect(await resp.text()).toContain('id="root"')
})

test('4. 带 hash 的静态资源长缓存 immutable', async ({ request }) => {
  const index = await (await request.get('/')).text()
  const match = index.match(/\/assets\/[^"']+\.js/)
  expect(match, 'index.html 应引用 /assets/*.js').toBeTruthy()
  const assetResp = await request.get(match![0])
  expect(assetResp.status()).toBe(200)
  expect((assetResp.headers()['cache-control'] ?? '').toLowerCase()).toContain('immutable')
})

test('5. 不存在的静态资源返回 404 而非回退 index', async ({ request }) => {
  const resp = await request.get('/assets/definitely-missing-abc123.js')
  expect(resp.status()).toBe(404)
  // nginx 自带 404 页本身是 HTML，但绝不允许回退成 SPA 的 index.html
  expect(await resp.text()).not.toContain('id="root"')
})

test('6. 登录失败经反代返回统一 JSON（非 index.html）', async ({ request }) => {
  const resp = await request.post('/api/v1/user/auth/login', {
    data: { account: '__deploy_nobody__', password: 'x' },
  })
  expect(resp.headers()['content-type'] ?? '').toContain('application/json')
  const json = await resp.json()
  expect(json.code).not.toBe(0)
  expect(json.message).toBeTruthy()
  expect(json.traceId).toBeTruthy()
})

test('7. 未知 API 路径不回退 index.html', async ({ request }) => {
  const resp = await request.get('/api/v1/definitely-not-exists')
  const ct = resp.headers()['content-type'] ?? ''
  const body = await resp.text()
  expect(ct).not.toContain('text/html')
  expect(body).not.toContain('id="root"')
})

test('8. 管理员经 nginx 全链路登录成功', async ({ request }) => {
  const token = await loginViaProxy(request, 'admin', 'Admin@123456')
  expect(token).toBeTruthy()
})

test('9. 部署学员经 nginx 全链路登录成功', async ({ request }) => {
  const token = await loginViaProxy(request, state.learnerAccount, state.learnerPassword)
  expect(token).toBeTruthy()
})

test('10. 视频 multipart 上传经 nginx 反代成功（500MB 能力链路）+ 发布', async ({ request }) => {
  const token = await loginViaProxy(request, 'admin', 'Admin@123456')
  const videoBytes = readFileSync(join(__dirname, 'fixtures', 'flower.mp4'))
  // 草稿态上传（已发布课程禁止替换视频，与业务约束一致）
  const uploadResp = await request.post(`/api/v1/course/lessons/${state.lessonId}/video`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: { name: 'flower.mp4', mimeType: 'video/mp4', buffer: videoBytes },
    },
  })
  const uploadJson = await uploadResp.json()
  expect(uploadJson.code, JSON.stringify(uploadJson)).toBe(0)
  const publishResp = await request.post(`/api/v1/course/courses/${state.courseId}/publish`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const publishJson = await publishResp.json()
  expect(publishJson.code, JSON.stringify(publishJson)).toBe(0)
})

test('11. 公开课程列表可匿名浏览 + 登录后可读（DB 链路）', async ({ request }) => {
  // 匿名公开浏览（Stage 2.1：打开即可浏览，个人数据才要求登录）
  const anon = await request.get('/api/v1/course/public/courses?pageNum=1&pageSize=20')
  const anonJson = await anon.json()
  expect(anonJson.code, JSON.stringify(anonJson)).toBe(0)
  const anonRows = (anonJson.data?.list ?? []) as { id: number; title: string }[]
  expect(
    anonRows.some((r) => r.id === state.courseId && r.title === 'Deploy Course'),
    `匿名公开列表应包含 Deploy Course，实际：${JSON.stringify(anonRows)}`,
  ).toBe(true)
  // 登录后同样可读（续播等个人数据不在匿名接口出现）
  const token = await loginViaProxy(request, state.learnerAccount, state.learnerPassword)
  const resp = await request.get('/api/v1/course/public/courses?pageNum=1&pageSize=20', {
    headers: { Authorization: `Bearer ${token}` },
  })
  const json = await resp.json()
  expect(json.code, JSON.stringify(json)).toBe(0)
})

test('12. 播放凭证 URL 为浏览器可达 Host（minio public-endpoint）', async ({ request }) => {
  const token = await loginViaProxy(request, state.learnerAccount, state.learnerPassword)
  const resp = await request.get(`/api/v1/course/lessons/${state.lessonId}/play`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const json = await resp.json()
  expect(json.code, JSON.stringify(json)).toBe(0)
  const url = json.data.url as string
  expect(url, '播放 URL 必须浏览器可达，不得为容器内网 DNS').toMatch(/^http:\/\/localhost:9000\//)
  expect(url).not.toContain('minio:9000')
  expect(url).toContain('X-Amz-Signature')
})

test('13. server actuator 健康检查 UP（直连 8080）', async ({ request }) => {
  const resp = await request.get('http://localhost:8080/actuator/health')
  expect(resp.status()).toBe(200)
  expect(await resp.text()).toContain('"UP"')
})

test('14. MinIO 存活探针（直连 9000）', async ({ request }) => {
  const resp = await request.get('http://localhost:9000/minio/health/live')
  expect(resp.status()).toBe(200)
})

test('15. 打开即是产品首页 → Demo 一键进入学习工作台', async ({ page }) => {
  // Stage 2.1：http://localhost 首先看到质感首页，而不是登录墙
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '学习，从这里开始' })).toBeVisible()
  // Demo 一键进入（真实 JWT 登录，仅免除输入账号密码；按钮仅在演示构建存在）
  await page.getByRole('button', { name: /进入 Demo/ }).click()
  await expect(page.getByRole('heading', { name: /你好/ })).toBeVisible({ timeout: 15_000 })
  await expect(page).toHaveURL(/\/$/)
})
