import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  mkdtemp,
  mkdir,
  writeFile,
  readFile,
  readdir,
  rm,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

test('artifact comparison CLI detects added, removed, changed and unchanged files without rewriting', async () => {
  for (const variant of ['unchanged', 'added', 'removed', 'changed']) {
    const directory = await mkdtemp(join(tmpdir(), 'generated-test-'));
    try {
      const expected = join(directory, 'expected');
      const actual = join(directory, 'actual');
      await mkdir(expected);
      await mkdir(actual);
      await writeFile(join(expected, 'api.txt'), 'generated\n');
      if (variant !== 'removed')
        await writeFile(
          join(actual, 'api.txt'),
          variant === 'changed' ? 'stale\n' : 'generated\n',
        );
      if (variant === 'added')
        await writeFile(join(actual, 'extra.txt'), 'extra\n');
      const snapshot = async () =>
        Promise.all(
          (await readdir(actual))
            .sort()
            .map(async (file) => [
              file,
              await readFile(join(actual, file), 'utf8'),
            ]),
        );
      const before = await snapshot();
      const result = spawnSync(
        process.execPath,
        ['scripts/check-generated.mjs', expected, actual],
        { encoding: 'utf8' },
      );
      assert.equal(
        result.status,
        variant === 'unchanged' ? 0 : 1,
        `${variant}: ${result.stderr}`,
      );
      if (variant !== 'unchanged')
        assert.match(result.stderr, /Generated file.*differs/);
      assert.deepEqual(await snapshot(), before, variant);
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  }
});
