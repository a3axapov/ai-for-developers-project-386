import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

test('checkpoint CLI fixture stays isolated inside a linked-worktree hk hook', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'checkpoint-hook-'));
  const repository = join(directory, 'repository');
  const linked = join(directory, 'linked');
  const env = { ...process.env };
  delete env.NODE_TEST_CONTEXT;
  const localVariables = spawnSync('git', ['rev-parse', '--local-env-vars'], {
    encoding: 'utf8',
  });
  assert.equal(localVariables.status, 0, localVariables.stderr);
  for (const name of localVariables.stdout.trim().split('\n')) delete env[name];
  const run = (cwd, command, args) => {
    const result = spawnSync(command, args, { cwd, env, encoding: 'utf8' });
    assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
    return result;
  };
  const quote = (value) => `'${value.replaceAll("'", "'\\''")}'`;
  try {
    await mkdir(repository);
    run(repository, 'git', ['init', '-b', 'fixture']);
    run(repository, 'git', ['config', 'user.email', 'test@example.invalid']);
    run(repository, 'git', ['config', 'user.name', 'Test']);
    run(repository, 'git', ['commit', '--allow-empty', '-m', 'initial']);
    run(repository, 'git', ['worktree', 'add', '-b', 'linked', linked]);
    await writeFile(join(linked, 'AGENTS.md'), '# Hook repository guidance\n');
    const command = `cd ${quote(process.cwd())} && ${quote(process.execPath)} --test ${quote(resolve('scripts/tests/checkpoint.test.mjs'))}`;
    await writeFile(
      join(linked, 'hk.pkl'),
      `amends "package://github.com/jdx/hk/releases/download/v2.12.0/hk@2.12.0#/Config.pkl"
hooks {
  ["pre-push"] {
    steps {
      ["checkpoint"] { check = ${JSON.stringify(command)} }
    }
  }
}
`,
    );
    const hook = join(repository, '.git/hooks/pre-push');
    await writeFile(
      hook,
      '#!/bin/sh\nexec hk run pre-push --from-hook "$@"\n',
      {
        mode: 0o755,
      },
    );
    run(linked, 'git', ['add', 'hk.pkl', 'AGENTS.md']);
    run(linked, 'git', ['commit', '-m', 'hook configuration']);
    const hooked = run(linked, 'git', [
      'hook',
      'run',
      'pre-push',
      '--',
      'origin',
      directory,
    ]);
    assert.match(
      hooked.stdout + hooked.stderr,
      /checkpoint persists outside tracked files/,
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
