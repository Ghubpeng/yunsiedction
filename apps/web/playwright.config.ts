import { defineConfig } from '@playwright/test'

/**
 * Stage 1.8 浏览器 E2E：真实前端（Vite dev server + /api 代理）+ 真实后端（MySQL/MinIO）。
 * 后端经 mvn spring-boot:run 启动（reuseExistingServer：本地已运行时复用）。
 */
export default defineConfig({
  testDir: './e2e',
  testIgnore: /deploy\.spec\.ts/,
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    locale: 'zh-CN',
  },
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  webServer: [
    {
      command: 'mvn -pl yunsie-server-boot spring-boot:run',
      cwd: '../server',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 240_000,
      // dev E2E 覆盖 Demo 登录体验（仅测试进程环境变量；生产默认关闭）
      env: { ...process.env, YUNSIE_DEMO_ENABLED: 'true' },
    },
    {
      command: 'npm run dev',
      cwd: '.',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
})
