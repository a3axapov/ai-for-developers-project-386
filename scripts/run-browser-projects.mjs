import { spawn } from 'node:child_process';

const [command, ...args] = process.argv.slice(2);
if (!command) {
  console.error(
    'Usage: node scripts/run-browser-projects.mjs <command> [args...]',
  );
  process.exit(1);
}
if (
  args.some((arg) =>
    /^(--(project|output|workers|config)(=|$)|-[jc])/.test(arg),
  )
) {
  console.error(
    'Project, output, config and worker overrides would break browser group isolation; use the sequential command instead.',
  );
  process.exit(1);
}

const groups = [
  { name: 'desktop', port: '4173', projects: ['desktop'] },
  { name: 'mobile', port: '4175', projects: ['mobile', 'configured-owner'] },
];

const processGroups = new Set();
let interrupted;
let killTimer;
function signalChildren(signal) {
  for (const pid of processGroups) {
    try {
      // A group includes Playwright's workers, browsers and application servers.
      process.kill(-pid, signal);
    } catch (error) {
      if (error.code !== 'ESRCH') throw error;
    }
  }
}
function stop(signal) {
  if (interrupted) return;
  interrupted = signal;
  signalChildren('SIGTERM');
  killTimer = setTimeout(() => signalChildren('SIGKILL'), 10_000);
  killTimer.unref();
}
const onInterrupt = () => stop('SIGINT');
const onTerminate = () => stop('SIGTERM');
process.on('SIGINT', onInterrupt);
process.on('SIGTERM', onTerminate);

const results = await Promise.all(
  groups.map(
    ({ name, port, projects }) =>
      new Promise((resolve) => {
        const child = spawn(
          command,
          [
            ...args,
            ...projects.map((project) => `--project=${project}`),
            `--output=test-results/${name}`,
          ],
          {
            stdio: 'inherit',
            env: { ...process.env, PW_PORT: port },
            detached: true,
          },
        );
        if (child.pid) processGroups.add(child.pid);
        child.on('error', (error) => {
          console.error(`${name}: ${error.message}`);
          resolve(false);
        });
        child.on('exit', (code) => {
          // Keep interrupted groups until final cleanup, even if their leader
          // exits before an application server or browser does.
          if (!interrupted) processGroups.delete(child.pid);
          resolve(code === 0);
        });
      }),
  ),
);
if (interrupted) signalChildren('SIGKILL');
clearTimeout(killTimer);
process.off('SIGINT', onInterrupt);
process.off('SIGTERM', onTerminate);
process.exitCode = interrupted
  ? interrupted === 'SIGINT'
    ? 130
    : 143
  : results.every(Boolean)
    ? 0
    : 1;
