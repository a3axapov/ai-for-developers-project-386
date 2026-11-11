import { setTimeout } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

export async function waitForHttp(url, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000) });
      if (response.ok) return response;
      lastError = new Error(`HTTP ${response.status}`);
      await response.body?.cancel();
    } catch (error) {
      lastError = error;
    }
    await setTimeout(500);
  }
  throw new Error(`Timed out waiting for ${url}`, { cause: lastError });
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  await waitForHttp(process.argv[2]);
}
