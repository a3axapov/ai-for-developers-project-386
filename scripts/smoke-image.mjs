import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { waitForHttp } from './wait-http.mjs';

function docker(...args) {
  return execFileSync('docker', args, { encoding: 'utf8' }).trim();
}

const image = process.env.IMAGE_NAME ?? 'calendar:local';
let container;
try {
  container = docker('run', '--detach', '--publish', '127.0.0.1::8080', image);
  const port = docker('port', container, '8080/tcp').split(':').at(-1);
  const origin = `http://127.0.0.1:${port}`;
  const health = await waitForHttp(`${origin}/api/health`);
  assert.deepEqual(await health.json(), { status: 'UP' });

  const page = await fetch(origin, { signal: AbortSignal.timeout(5000) });
  assert.equal(page.status, 200);
  const html = await page.text();
  assert.match(html, /<div id="root"><\/div>/);
  const assetPath = html.match(/src="([^"]+\.js)"/)?.[1];
  assert.ok(assetPath, 'Frontend entry asset is missing');
  const asset = await fetch(new URL(assetPath, origin), {
    signal: AbortSignal.timeout(5000),
  });
  assert.equal(asset.status, 200);
  assert.match(asset.headers.get('content-type'), /javascript/);
  await asset.body?.cancel();

  const route = await fetch(`${origin}/booking/example`, {
    signal: AbortSignal.timeout(5000),
  });
  assert.equal(route.status, 200);
  assert.equal(await route.text(), html);

  const missingAsset = await fetch(`${origin}/assets/does-not-exist.js`, {
    signal: AbortSignal.timeout(5000),
  });
  assert.equal(missingAsset.status, 404);
  await missingAsset.body?.cancel();

  const missingApi = await fetch(`${origin}/api/does-not-exist`, {
    signal: AbortSignal.timeout(5000),
  });
  assert.equal(missingApi.status, 404);
  await missingApi.body?.cancel();
  assert.equal(
    docker('inspect', '--format', '{{.Config.User}}', container),
    '10001:10001',
  );
  console.log(`Container smoke test passed: ${image}`);
} catch (error) {
  if (container) {
    console.error(docker('logs', container));
  }
  throw error;
} finally {
  if (container) docker('rm', '--force', container);
}
