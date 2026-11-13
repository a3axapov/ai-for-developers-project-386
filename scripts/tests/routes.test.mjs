import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

test('route CLI rejects the old public entry literal/parameter collision', async () => {
  const directory = await mkdtemp(resolve('.route-test-'));
  try {
    const source = resolve(directory, 'main.tsp');
    await writeFile(
      source,
      `import "@typespec/http";
using TypeSpec.Http;
@service namespace Fixture;
@get @route("/public/entry") op entry(): string;
@get @route("/public/{username}") op page(@path username: string): string;
`,
    );
    const result = spawnSync(
      process.execPath,
      ['scripts/check-routes.mjs', source],
      { encoding: 'utf8' },
    );
    assert.equal(result.status, 1);
    assert.match(
      result.stderr,
      /Overlapping GET routes.*\/public\/entry.*\/public\/\{username\}/,
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

for (const [name, routes, expected] of [
  [
    'parameters in different positions overlap',
    '@get @route("/{user}/edit") op a(@path user: string): string;\n@get @route("/owner/{section}") op b(@path section: string): string;',
    1,
  ],
  [
    'renaming parameters still overlaps',
    '@get @route("/public/{user}") op a(@path user: string): string;\n@get @route("/public/{name}") op b(@path name: string): string;',
    1,
  ],
  [
    'different lengths are disjoint',
    '@get @route("/public/{user}") op a(@path user: string): string;\n@get @route("/public/entry/current") op b(): string;',
    0,
  ],
  [
    'different methods are disjoint',
    '@get @route("/public/entry") op a(): string;\n@post @route("/public/{user}") op b(@path user: string): string;',
    0,
  ],
  [
    'different literals are disjoint',
    '@get @route("/owner/{id}/slots") op a(@path id: string): string;\n@get @route("/public/{id}/slots") op b(@path id: string): string;',
    0,
  ],
  [
    'namespace routes resolve from source',
    '@route("/public") namespace Nested { @get @route("/entry") op a(): string; @get @route("/{user}") op b(@path user: string): string; }',
    1,
  ],
]) {
  test(`route CLI: ${name}`, async () => {
    const directory = await mkdtemp(resolve('.route-test-'));
    try {
      const source = resolve(directory, 'main.tsp');
      await writeFile(
        source,
        `import "@typespec/http"; using TypeSpec.Http; @service namespace Fixture;\n${routes}`,
      );
      const result = spawnSync(
        process.execPath,
        ['scripts/check-routes.mjs', source],
        { encoding: 'utf8' },
      );
      assert.equal(result.status, expected, result.stderr);
      if (expected) assert.match(result.stderr, /Overlapping GET routes/);
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  });
}
