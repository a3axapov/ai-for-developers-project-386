import { defineConfig, devices } from '@playwright/test';
import { readOnlyPort, readOnlyURL } from './browser-ports';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  use: {
    baseURL: readOnlyURL,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'desktop',
      testIgnore: 'configured-owner.spec.ts',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'mobile',
      testIgnore: 'configured-owner.spec.ts',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 320, height: 720 },
      },
    },
    {
      name: 'configured-owner',
      testMatch: 'configured-owner.spec.ts',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: `java -jar ../build/libs/calendar.jar --server.address=127.0.0.1 --server.port=${readOnlyPort}`,
      url: `${readOnlyURL}/api/health`,
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
    },
  ],
});
