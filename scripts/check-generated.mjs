import { readdir, readFile } from 'node:fs/promises';
import { join, relative } from 'node:path';

async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  return (
    await Promise.all(
      entries.map((entry) => {
        const path = join(directory, entry.name);
        return entry.isDirectory() ? files(path) : [path];
      }),
    )
  ).flat();
}

try {
  const [expectedDirectory, actualDirectory, ...extra] = process.argv.slice(2);
  if (!expectedDirectory || !actualDirectory || extra.length)
    throw new Error('Usage: check-generated.mjs EXPECTED ACTUAL');
  const expected = (await files(expectedDirectory))
    .map((path) => relative(expectedDirectory, path))
    .sort();
  const actual = (await files(actualDirectory))
    .map((path) => relative(actualDirectory, path))
    .sort();
  if (JSON.stringify(expected) !== JSON.stringify(actual))
    throw new Error(`Generated file list differs in ${actualDirectory}`);
  for (const path of expected) {
    const [left, right] = await Promise.all([
      readFile(join(expectedDirectory, path)),
      readFile(join(actualDirectory, path)),
    ]);
    if (!left.equals(right))
      throw new Error(`Generated file differs: ${join(actualDirectory, path)}`);
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
