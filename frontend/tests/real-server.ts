import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, writeFile, rename, rm } from 'node:fs/promises';
import { resolve } from 'node:path';
import { expect, test as base } from '@playwright/test';
import { mutablePort, mutableURL } from '../browser-ports';

// Each mutable scenario gets its own real application state on its group's port.
export const test = base.extend<{
  ownerUsername: string;
  serverArguments: string[];
  fixedNow: string | undefined;
  serverLifecycle: {
    restart?: () => Promise<void>;
    setNow?: (instant: string) => Promise<void>;
  };
  setServerNow: (instant: string) => Promise<void>;
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
  setServerNow: async ({ baseURL, serverLifecycle }, use) => {
    void baseURL;
    await use((instant) => serverLifecycle.setNow!(instant));
  },
  baseURL: async (
    { playwright, ownerUsername, serverArguments, fixedNow, serverLifecycle },
    use,
  ) => {
    const clockDirectory = fixedNow
      ? await mkdtemp(resolve('../build/browser-clock-'))
      : undefined;
    const clockFile = clockDirectory && resolve(clockDirectory, 'now');
    if (clockFile) await writeFile(clockFile, fixedNow!);
    serverLifecycle.setNow = async (instant) => {
      if (!clockFile) throw new Error('setServerNow requires fixedNow');
      await writeFile(`${clockFile}.next`, instant);
      await rename(`${clockFile}.next`, clockFile);
    };
    const launcher = fixedNow
      ? [
          '-Dloader.path=../build/classes/java/test',
          '-Dloader.main=com.testproject.calendar.fixtures.SlotBrowserApplication',
          '-cp',
          '../build/libs/calendar.jar',
          'org.springframework.boot.loader.launch.PropertiesLauncher',
          `--slot-test.now=${fixedNow}`,
          `--slot-test.clock-file=${clockFile}`,
        ]
      : ['-jar', '../build/libs/calendar.jar'];
    const launch = () =>
      spawn('java', [
        ...launcher,
        '--server.address=127.0.0.1',
        `--server.port=${mutablePort}`,
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
              return (await client.get(`${mutableURL}/api/health`)).status();
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
      await use(mutableURL);
    } finally {
      await client.dispose();
      await stop();
      if (clockDirectory) await rm(clockDirectory, { recursive: true });
    }
  },
});

export { expect } from '@playwright/test';
