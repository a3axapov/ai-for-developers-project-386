import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { expect, test as base } from '@playwright/test';

// Each mutable scenario gets its own real application state; the read-only suite uses 4173.
export const test = base.extend<{
  ownerUsername: string;
  serverArguments: string[];
  fixedNow: string | undefined;
  serverLifecycle: { restart?: () => Promise<void> };
  restartServer: () => Promise<void>;
}>({
  ownerUsername: ['anna-1', { option: true }],
  serverArguments: [[], { option: true }],
  fixedNow: [undefined, { option: true }],
  serverLifecycle: async ({ ownerUsername }, use) => {
    void ownerUsername;
    await use({});
  },
  restartServer: async ({ baseURL, serverLifecycle }, use) => {
    void baseURL;
    await use(() => serverLifecycle.restart!());
  },
  baseURL: async (
    { playwright, ownerUsername, serverArguments, fixedNow, serverLifecycle },
    use,
  ) => {
    const launcher = fixedNow
      ? [
          '-Dloader.path=../build/classes/java/test',
          '-Dloader.main=com.testproject.calendar.fixtures.SlotBrowserApplication',
          '-cp',
          '../build/libs/calendar.jar',
          'org.springframework.boot.loader.launch.PropertiesLauncher',
          `--slot-test.now=${fixedNow}`,
        ]
      : ['-jar', '../build/libs/calendar.jar'];
    const launch = () =>
      spawn('java', [
        ...launcher,
        '--server.address=127.0.0.1',
        '--server.port=4174',
        '--spring.config.additional-location=file:tests/fixtures/owner.yaml',
        `--calendar.initial.username=${ownerUsername}`,
        ...serverArguments,
      ]);
    let server = launch();
    let output = '';
    const captureOutput = () => {
      server.stdout.on('data', (chunk: Buffer) => {
        output += chunk.toString();
      });
      server.stderr.on('data', (chunk: Buffer) => {
        output += chunk.toString();
      });
    };
    captureOutput();
    const client = await playwright.request.newContext();
    const ready = async () => {
      await expect
        .poll(
          async () => {
            if (server.exitCode !== null) throw new Error(output);
            try {
              return (
                await client.get('http://127.0.0.1:4174/api/health')
              ).status();
            } catch {
              return 0;
            }
          },
          { timeout: 30_000 },
        )
        .toBe(200);
    };
    const stop = async () => {
      if (server.exitCode === null) {
        const exited = once(server, 'exit');
        server.kill('SIGTERM');
        await exited;
      }
    };
    serverLifecycle.restart = async () => {
      await stop();
      output = '';
      server = launch();
      captureOutput();
      await ready();
    };
    try {
      await ready();
      await use('http://127.0.0.1:4174');
    } finally {
      await client.dispose();
      await stop();
    }
  },
});

export { expect } from '@playwright/test';
