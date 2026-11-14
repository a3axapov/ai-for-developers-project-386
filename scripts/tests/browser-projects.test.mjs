import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setTimeout } from 'node:timers/promises';

test('browser runner overlaps both groups with separate ports and output directories', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'browser-runner-'));
  try {
    const cli = join(directory, 'browser.mjs');
    await writeFile(
      cli,
      `
      import { writeFile, access } from 'node:fs/promises';
      import { setTimeout } from 'node:timers/promises';
      const port = process.env.PW_PORT;
      await writeFile(${JSON.stringify(directory)} + '/' + port, JSON.stringify(process.argv.slice(2)));
      const peer = port === '4173' ? '4175' : '4173';
      // Neither process can finish unless its peer has started.
      for (let i = 0; i < 100; i++) {
        try {
          await access(${JSON.stringify(directory)} + '/' + peer);
          process.exit(0);
        } catch { await setTimeout(20); }
      }
      process.exit(2);
      `,
    );
    const result = spawnSync(
      process.execPath,
      [
        'scripts/run-browser-projects.mjs',
        process.execPath,
        cli,
        '--grep=smoke',
      ],
      { encoding: 'utf8', timeout: 10_000 },
    );
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(await readFile(join(directory, '4173'))), [
      '--grep=smoke',
      '--project=desktop',
      '--output=test-results/desktop',
    ]);
    assert.deepEqual(JSON.parse(await readFile(join(directory, '4175'))), [
      '--grep=smoke',
      '--project=mobile',
      '--project=configured-owner',
      '--output=test-results/mobile',
    ]);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('browser runner rejects overrides of project, output and worker isolation', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'browser-runner-'));
  try {
    const cli = join(directory, 'browser.mjs');
    await writeFile(cli, 'process.exit(0);');
    for (const option of [
      '--project=desktop',
      '--output=shared',
      '--workers=2',
      '-j2',
      '--config=other.ts',
      '-c',
    ]) {
      const result = spawnSync(
        process.execPath,
        ['scripts/run-browser-projects.mjs', process.execPath, cli, option],
        { encoding: 'utf8', timeout: 10_000 },
      );
      assert.equal(result.status, 1, `${option}: ${result.stderr}`);
      assert.match(result.stderr, /isolation/);
    }
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

for (const failedPort of ['4173', '4175']) {
  test(`browser runner reports failure of ${failedPort} and waits for the other group`, async () => {
    const directory = await mkdtemp(join(tmpdir(), 'browser-runner-'));
    try {
      const cli = join(directory, 'browser.mjs');
      await writeFile(
        cli,
        `
        import { setTimeout } from 'node:timers/promises';
        if (process.env.PW_PORT === '${failedPort}') process.exit(7);
        await setTimeout(100);
        console.log('other group finished');
        `,
      );
      const result = spawnSync(
        process.execPath,
        ['scripts/run-browser-projects.mjs', process.execPath, cli],
        { encoding: 'utf8', timeout: 10_000 },
      );
      assert.equal(result.status, 1, result.stderr);
      assert.match(result.stdout, /other group finished/);
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  });
}

test('browser runner reports a missing executable as failure', () => {
  const result = spawnSync(
    process.execPath,
    ['scripts/run-browser-projects.mjs', '/nonexistent/browser-command'],
    { encoding: 'utf8', timeout: 10_000 },
  );
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, /ENOENT/);
});

test('SIGTERM stops both browser process trees and returns a nonzero status', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'browser-runner-'));
  let runner;
  const pids = [];
  try {
    const cli = join(directory, 'browser.mjs');
    await writeFile(
      cli,
      `
      import { spawn } from 'node:child_process';
      import { writeFile } from 'node:fs/promises';
      const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
      child.on('exit', () => process.exit(0));
      // Reap our child after the process group receives SIGTERM.
      process.on('SIGTERM', () => {});
      await writeFile(${JSON.stringify(directory)} + '/' + process.env.PW_PORT, JSON.stringify([process.pid, child.pid]));
      `,
    );
    runner = spawn(
      process.execPath,
      ['scripts/run-browser-projects.mjs', process.execPath, cli],
      { stdio: 'ignore' },
    );
    const exited = once(runner, 'exit');
    for (const port of ['4173', '4175']) {
      let recorded;
      for (let i = 0; i < 100; i++) {
        try {
          recorded = JSON.parse(await readFile(join(directory, port)));
          break;
        } catch {
          await setTimeout(20);
        }
      }
      assert.ok(recorded, `group ${port} did not start`);
      pids.push(...recorded);
    }
    runner.kill('SIGTERM');
    const [code] = await exited;
    assert.equal(code, 143);
    for (const pid of pids) {
      assert.throws(() => process.kill(pid, 0), { code: 'ESRCH' });
    }
  } finally {
    runner?.kill('SIGKILL');
    for (const pid of pids) {
      try {
        process.kill(pid, 'SIGKILL');
      } catch {
        // Already stopped by the runner.
      }
    }
    await rm(directory, { recursive: true, force: true });
  }
});
