import { execFileSync } from 'node:child_process';
import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createClient } from '@hey-api/openapi-ts';

const root = fileURLToPath(new URL('../', import.meta.url));
const checking = process.argv.includes('--check');
const temporary = checking
  ? await mkdtemp(join(tmpdir(), 'calendar-api-'))
  : undefined;
const openapiDirectory = join(temporary ?? root, 'api/openapi');
const clientDirectory = join(temporary ?? root, 'frontend/src/api/generated');

async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const paths = await Promise.all(
    entries.map((entry) => {
      const path = join(directory, entry.name);
      return entry.isDirectory() ? files(path) : [path];
    }),
  );
  return paths.flat();
}

async function compare(expectedDirectory, actualDirectory) {
  const expected = (await files(expectedDirectory))
    .map((path) => relative(expectedDirectory, path))
    .sort();
  const actual = (await files(actualDirectory))
    .map((path) => relative(actualDirectory, path))
    .sort();
  if (JSON.stringify(expected) !== JSON.stringify(actual)) {
    throw new Error(`Generated file list differs in ${actualDirectory}`);
  }
  for (const path of expected) {
    const [left, right] = await Promise.all([
      readFile(join(expectedDirectory, path)),
      readFile(join(actualDirectory, path)),
    ]);
    if (!left.equals(right)) {
      throw new Error(`Generated file differs: ${join(actualDirectory, path)}`);
    }
  }
}

try {
  execFileSync(
    process.execPath,
    [
      resolve(root, 'node_modules/@typespec/compiler/cmd/tsp.js'),
      'compile',
      'api',
      '--output-dir',
      openapiDirectory,
      '--warn-as-error',
    ],
    { cwd: root, stdio: 'inherit' },
  );
  await createClient({
    input: join(openapiDirectory, 'openapi.yaml'),
    output: clientDirectory,
    plugins: ['@hey-api/typescript', '@hey-api/client-fetch', '@hey-api/sdk'],
  });
  if (checking) {
    await compare(openapiDirectory, join(root, 'api/openapi'));
    await compare(clientDirectory, join(root, 'frontend/src/api/generated'));
    console.log('OpenAPI and generated client match TypeSpec.');
  }
} catch (error) {
  console.error(error.message);
  if (checking)
    console.error('Run mise run api:generate and commit the outputs.');
  process.exitCode = 1;
} finally {
  if (temporary) await rm(temporary, { recursive: true, force: true });
}
