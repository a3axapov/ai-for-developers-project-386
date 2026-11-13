import { execFileSync } from 'node:child_process';
import { cp, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createClient } from '@hey-api/openapi-ts';

const root = fileURLToPath(new URL('../', import.meta.url));
const checking = process.argv.includes('--check');
const temporary = await mkdtemp(join(tmpdir(), 'calendar-api-'));
const openapiDirectory = join(temporary, 'api/openapi');
const clientDirectory = join(temporary, 'frontend/src/api/generated');
const serverDirectory = join(temporary, 'api/server');

async function compare(expectedDirectory, actualDirectory) {
  execFileSync(
    process.execPath,
    [
      join(root, 'scripts/check-generated.mjs'),
      expectedDirectory,
      actualDirectory,
    ],
    { stdio: 'inherit' },
  );
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
  const generatorClasspath = process.env.CALENDAR_GENERATOR_CLASSPATH;
  const serverCommand = generatorClasspath ? 'java' : resolve(root, 'gradlew');
  const serverArguments = generatorClasspath
    ? [
        '-cp',
        generatorClasspath,
        'org.openapitools.codegen.OpenAPIGenerator',
        'generate',
        '-i',
        join(openapiDirectory, 'openapi.yaml'),
        '-o',
        serverDirectory,
        '-c',
        'api/server-generator.json',
      ]
    : [
        '--no-daemon',
        'generateServerContract',
        `-PapiInput=${join(openapiDirectory, 'openapi.yaml')}`,
        `-PapiOutput=${serverDirectory}`,
      ];
  execFileSync(serverCommand, serverArguments, { cwd: root, stdio: 'inherit' });
  if (checking) {
    await compare(openapiDirectory, join(root, 'api/openapi'));
    await compare(clientDirectory, join(root, 'frontend/src/api/generated'));
    await compare(serverDirectory, join(root, 'api/server'));
    console.log('OpenAPI, generated server contract and SDK match TypeSpec.');
  } else {
    for (const path of [
      'api/openapi',
      'api/server',
      'frontend/src/api/generated',
    ]) {
      await rm(join(root, path), { recursive: true, force: true });
      await cp(join(temporary, path), join(root, path), { recursive: true });
    }
  }
} catch (error) {
  console.error(error.message);
  if (checking)
    console.error('Run mise run api:generate and commit the outputs.');
  process.exitCode = 1;
} finally {
  await rm(temporary, { recursive: true, force: true });
}
