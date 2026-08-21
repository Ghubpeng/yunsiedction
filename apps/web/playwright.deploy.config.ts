import { defineConfig } from '@playwright/test'

/**
 * Stage 2.0 生产部署冒烟：目标为 compose 交付栈（nginx:5173 → server:8080 → mysql/redis/minio）。
 * 栈由 scripts\up.ps1 启动；本配置不启动任何 webServer（生产形态不含 Vite dev / Maven 进程）。
 * 运行：npm run e2e:deploy（需先 scripts\up.ps1 完成且健康）。
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: /deploy\.spec\.ts/,
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
  globalSetup: './e2e/deploy-setup.ts',
  globalTeardown: './e2e/deploy-teardown.ts',
})
