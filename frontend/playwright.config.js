import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  timeout: 60000,
  expect: { timeout: 10000 },
  use: {
    baseURL: process.env.UI_BASE_URL || 'http://127.0.0.1:8080',
    browserName: 'chromium',
    trace: 'retain-on-failure',
  },
});
