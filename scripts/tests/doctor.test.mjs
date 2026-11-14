import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, writeFile, chmod, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createServer } from 'node:net';

test('doctor reports a wrong pinned tool and gives setup instructions', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'doctor-test-'));
  try {
    await writeFile(join(directory, 'npm'), '#!/bin/sh\necho 0.0.0\n');
    await chmod(join(directory, 'npm'), 0o755);
    const result = spawnSync(
      process.execPath,
      ['scripts/doctor.mjs', '--tools-only'],
      {
        encoding: 'utf8',
        env: { ...process.env, PATH: `${directory}:${process.env.PATH}` },
      },
    );
    assert.equal(result.status, 1);
    assert.match(result.stderr, /npm.*expected 12\.2\.2/);
    assert.match(result.stderr, /mise trust.*mise install/);
    assert.doesNotMatch(result.stdout, /Chromium/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('doctor diagnoses an unavailable executable without claiming success', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'doctor-missing-'));
  try {
    const result = spawnSync(
      process.execPath,
      ['scripts/doctor.mjs', '--tools-only'],
      {
        encoding: 'utf8',
        env: { ...process.env, PATH: directory },
      },
    );
    assert.equal(result.status, 1);
    assert.match(result.stderr, /FAIL java.*ENOENT/);
    assert.match(result.stderr, /mise install/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('doctor detects a genuinely occupied browser port and still launches real Chromium', async () => {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(4175, '127.0.0.1', resolve);
  });
  try {
    const result = spawnSync(process.execPath, ['scripts/doctor.mjs'], {
      encoding: 'utf8',
      timeout: 30_000,
    });
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stderr, /FAIL port 4175: EADDRINUSE/);
    assert.match(result.stdout, /OK Chromium launched and rendered a page/);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
