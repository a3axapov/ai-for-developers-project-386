import { execFileSync } from 'node:child_process';
import { mkdir, readFile, realpath, rename, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { join } from 'node:path';

// Deliberately no arbitrary notes or command arguments: no guest data or secrets.
const stages = [
  'implementing',
  'checking',
  'review',
  'ready',
  'paused',
  'blocked',
];
const verdicts = ['pending', 'changes-requested', 'approved'];
const commands = [
  'none',
  'doctor',
  'api:check',
  'lint',
  'typecheck',
  'test',
  'test:browser',
  'check',
  'image:build',
  'image:test',
  'push',
  'ci',
];
const git = (...args) =>
  execFileSync('git', args, {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
try {
  const [action, ticket, ...args] = process.argv.slice(2);
  if (!['save', 'show'].includes(action) || !/^[1-9]\d*$/.test(ticket ?? ''))
    throw new Error(
      'Usage: checkpoint.mjs save|show TICKET [--stage STAGE --review VERDICT --review-sha SHA --pending COMMAND]',
    );
  const worktree = await realpath(git('rev-parse', '--show-toplevel'));
  const branch = git('symbolic-ref', '--short', 'HEAD');
  const head = git('rev-parse', 'HEAD');
  const instructions = createHash('sha256')
    .update(await readFile(join(worktree, 'AGENTS.md')))
    .digest('hex');
  const directory = join(
    git('rev-parse', '--path-format=absolute', '--git-common-dir'),
    'agent-checkpoints',
  );
  const path = join(directory, `${ticket}.json`);
  if (action === 'save') {
    const options = {};
    for (let i = 0; i < args.length; i += 2) {
      if (
        !['--stage', '--review', '--review-sha', '--pending'].includes(
          args[i],
        ) ||
        !args[i + 1] ||
        options[args[i]]
      )
        throw new Error('Invalid checkpoint option');
      options[args[i]] = args[i + 1];
    }
    const stage = options['--stage'];
    const verdict = options['--review'];
    const pendingCommand = options['--pending'] ?? 'none';
    if (
      !stages.includes(stage) ||
      !verdicts.includes(verdict) ||
      !commands.includes(pendingCommand)
    )
      throw new Error(
        'Invalid stage, review or pending command; see docs/agents/workflow.md',
      );
    const reviewSha = options['--review-sha']
      ? git('rev-parse', '--verify', `${options['--review-sha']}^{commit}`)
      : null;
    if (verdict !== 'pending' && !reviewSha)
      throw new Error('A review verdict requires --review-sha');
    if (stage === 'ready' && (verdict !== 'approved' || reviewSha !== head))
      throw new Error('Ready requires approval of current HEAD');
    const state = {
      schemaVersion: 1,
      ticket: Number(ticket),
      branch,
      worktree,
      head,
      instructionsSha256: instructions,
      stage,
      review: { verdict, head: reviewSha },
      pendingCommand,
    };
    await mkdir(directory, { recursive: true });
    const temporary = `${path}.${process.pid}.tmp`;
    await writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`, {
      mode: 0o600,
    });
    await rename(temporary, path);
    console.log(`Checkpoint saved: ${path}`);
  } else {
    if (args.length) throw new Error('show accepts only the ticket number');
    const state = JSON.parse(await readFile(path, 'utf8'));
    if (
      state.schemaVersion !== 1 ||
      state.ticket !== Number(ticket) ||
      !stages.includes(state.stage) ||
      !verdicts.includes(state.review?.verdict) ||
      !commands.includes(state.pendingCommand)
    )
      throw new Error('Invalid checkpoint schema');
    for (const [field, actual] of Object.entries({
      worktree,
      branch,
      head,
      instructionsSha256: instructions,
    })) {
      if (state[field] !== actual)
        throw new Error(
          `${field === 'head' ? 'HEAD' : field} mismatch; read this worktree AGENTS.md, inspect git status/log and latest review SHA, then explicitly save a fresh checkpoint`,
        );
    }
    if (state.review.head && state.review.head !== head)
      console.error(
        `Review is stale: reviewed ${state.review.head}, current ${head}; fresh review required.`,
      );
    console.log(JSON.stringify(state, null, 2));
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
