import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

test('checkpoint persists outside tracked files and refuses a stale HEAD', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'checkpoint-test-'));
  const script = resolve('scripts/checkpoint.mjs');
  const git = (...args) =>
    spawnSync('git', args, { cwd: directory, encoding: 'utf8' });
  const cli = (...args) =>
    spawnSync(process.execPath, [script, ...args], {
      cwd: directory,
      encoding: 'utf8',
    });
  try {
    git('init', '-b', 'ticket/35');
    git('config', 'user.email', 'test@example.invalid');
    git('config', 'user.name', 'Test');
    await writeFile(join(directory, 'AGENTS.md'), '# Guidance\n');
    git('add', '.');
    git('commit', '-m', 'initial');
    const saved = cli(
      'save',
      '35',
      '--stage',
      'implementing',
      '--review',
      'pending',
      '--pending',
      'check',
    );
    assert.equal(saved.status, 0, saved.stderr);
    const shown = cli('show', '35');
    assert.equal(shown.status, 0, shown.stderr);
    const state = JSON.parse(shown.stdout);
    assert.equal(state.ticket, 35);
    assert.equal(state.head, git('rev-parse', 'HEAD').stdout.trim());
    assert.equal(state.pendingCommand, 'check');
    assert.equal(git('status', '--porcelain').stdout, '');
    await writeFile(join(directory, 'AGENTS.md'), '# Changed guidance\n');
    assert.match(cli('show', '35').stderr, /instructionsSha256 mismatch/);
    await writeFile(join(directory, 'AGENTS.md'), '# Guidance\n');
    const linked = join(directory, 'linked');
    git('worktree', 'add', '-b', 'other', linked);
    const wrongWorktree = spawnSync(process.execPath, [script, 'show', '35'], {
      cwd: linked,
      encoding: 'utf8',
    });
    assert.equal(wrongWorktree.status, 1);
    assert.match(wrongWorktree.stderr, /worktree mismatch/);
    git('worktree', 'remove', linked);
    assert.equal(cli('show', '35').status, 0);
    assert.match(
      await readFile(join(directory, '.git/agent-checkpoints/35.json'), 'utf8'),
      /ticket\/35/,
    );
    assert.equal(
      cli('save', '35', '--stage', 'ready', '--review', 'approved').status,
      1,
    );
    assert.equal(
      cli(
        'save',
        '35',
        '--stage',
        'paused',
        '--review',
        'pending',
        '--pending',
        'secret=value',
      ).status,
      1,
    );
    git('commit', '--allow-empty', '-m', 'next');
    assert.equal(cli('show', '35').status, 1);
    assert.match(cli('show', '35').stderr, /HEAD mismatch/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
