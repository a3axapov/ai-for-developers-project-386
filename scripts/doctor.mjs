import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:net';

const toolsOnly = process.argv.slice(2);
if (toolsOnly.some((arg) => arg !== '--tools-only')) {
  console.error('Usage: node scripts/doctor.mjs [--tools-only]');
  process.exit(1);
}
const config = await readFile(new URL('../mise.toml', import.meta.url), 'utf8');
const pin = (name) => config.match(new RegExp(`^${name} = "([^"]+)"`, 'm'))[1];
const checks = [
  ['mise', pin('min_version'), '--version'],
  ['node', pin('node'), '--version'],
  ['npm', pin('"npm:npm"'), '--version'],
  [
    'java',
    pin('java').replace('temurin-', '').replace('.0.LTS', ''),
    '-version',
  ],
  ['hk', pin('hk'), '--version'],
  ['pitchfork', pin('pitchfork'), '--version'],
];
for (const [command, expected, argument] of checks) {
  const result = spawnSync(command, [argument], {
    encoding: 'utf8',
    timeout: 10_000,
  });
  const version = `${result.stdout ?? ''}${result.stderr ?? ''}`;
  const escaped = expected.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  if (
    result.status !== 0 ||
    !new RegExp(`(^|[^\\d.])${escaped}(?=[^\\d.]|$)`).test(version)
  ) {
    console.error(
      `FAIL ${command}: expected ${expected}; ${result.error?.message ?? version.trim()}. Run mise trust && mise install, then mise run doctor from the worktree root.`,
    );
    process.exitCode = 1;
  } else console.log(`OK ${command} ${expected}`);
}
if (!toolsOnly.length) {
  for (const port of [4173, 4174]) {
    await new Promise((resolve) => {
      const server = createServer();
      server.once('error', (error) => {
        console.error(
          `FAIL port ${port}: ${error.code}. Stop the owning test/dev server before running browser tests.`,
        );
        process.exitCode = 1;
        resolve();
      });
      server.listen(port, '127.0.0.1', () =>
        server.close(() => {
          console.log(`OK port ${port} available (not reserved)`);
          resolve();
        }),
      );
    });
  }
  let browser;
  try {
    const { chromium } = await import('@playwright/test');
    browser = await chromium.launch({ timeout: 15_000 });
    const page = await browser.newPage();
    await page.setContent('<title>doctor</title>');
    if ((await page.title()) !== 'doctor')
      throw new Error('Unexpected browser result');
    console.log(
      'OK Chromium launched and rendered a page (no application build)',
    );
  } catch (error) {
    console.error(
      `FAIL Chromium: ${error.message}\nRun mise run test:browser:install. For missing system libraries: ./node_modules/.bin/playwright install-deps --dry-run chromium; ask the user to run sudo ./node_modules/.bin/playwright install-deps chromium. Do not change system files or use library-path workarounds.`,
    );
    process.exitCode = 1;
  } finally {
    await browser?.close();
  }
}
