# Environment and verification

## Setup

- Run commands from the repository root through mise.
- Bootstrap with `mise trust`, then `mise install`; its postinstall hook runs `npm ci` and installs hk hooks outside CI.
- `.npmrc` enforces exact Node/npm versions, so system npm may fail even when Node matches.
- Keep `npm:npm` before `node` in `mise.toml`: otherwise Node's bundled npm takes precedence. Ensure the mise executable is on `PATH` because tasks invoke mise recursively.
- If `mise` is not on `PATH`, use `export PATH="$HOME/.local/bin:$PATH"`; do not reinstall mise.

## Verification

- Quick readiness check (no build): `mise run doctor` checks pinned command versions, launches real Chromium and probes ports 4173/4174 without keeping them reserved. It never installs tools or system packages. `--tools-only` is a narrower diagnostic, not browser evidence.
- Resume checkpoints, worktree instruction checks, review handoff and SSH/CI guidance: [workflow.md](workflow.md). Existing regression coverage and maintenance CLI tests: [retro-regressions.md](retro-regressions.md).

- `mise run check` is the single local/CI gate: `api:check` → lint/format → typecheck → JUnit including real HTTP API tests → application build with the frontend → Playwright against real Spring Boot servers.
- `mise run check:fast` runs the same gate without the frontend build and Playwright. Use it during development; it is not final acceptance. The pre-push hook runs this faster suite, not the full gate.
- Locally, `mise run test:browser` (and therefore `check`) builds the frontend and JAR once, then runs desktop and mobile/configured-owner concurrently, with one worker per group. The groups use separate server pairs (`4173/4174` and `4175/4176`) and result directories (`frontend/test-results/desktop` and `frontend/test-results/mobile`). Do not run another browser suite concurrently on these ports, including from another worktree.
- With `CI` set, `test:browser` keeps the sequential run. Use `mise run test:browser:sequential` or `mise run test:browser:parallel` to choose explicitly; both run all projects. The parallel command forwards filters to both groups, but owns project selection and output directories.
- For a targeted/sequential run, `PW_PORT` optionally selects the read-only server port (default `4173`); mutable scenarios use the next port. The parallel runner assigns its own port pairs.
- During TDD, select the affected JUnit test or Playwright scenario. For example, `mise run test:browser:targeted -- tests/navigation.spec.ts --project desktop --grep 'главная'` builds the frontend and JAR before running the selected tests against real Spring Boot servers. This does not reuse a potentially stale JAR or substitute API responses.
- After final review fixes and integration, run the full `mise run check` once on the final tree before pushing. Any subsequent code/configuration change invalidates that result; rerun the gate. Do not repeat it just because the same tree moves between roles or worktrees.
- Only the coordinator pushes the integration branch, normally once per ticket after the full gate; implementers/reviewers keep their commits local. Wait for CI once for that exact SHA. A failed CI may require a corrective push after fixes and renewed verification. Do not skip hooks as part of the normal workflow.
- Install Chromium and its system dependencies once with `mise run test:browser:install`.
- If Chromium fails to start, list missing packages with `./node_modules/.bin/playwright install-deps --dry-run chromium` and ask the user to run `sudo ./node_modules/.bin/playwright install-deps chromium`. Do not change tests, disable the browser, or edit system files to hide the failure.
- Focused Java test: `mise exec -- ./gradlew --no-daemon test --tests 'com.testproject.calendar.CalendarApplicationTests.applicationRespondsToHttpRequest'`. Tests start their own HTTP server on a random port; no running dev server, Docker, or database is required.
- Frontend-only: `mise run typecheck` or `mise exec -- npm run build --workspace frontend`. The workspace build consumes the existing generated client; it does not regenerate it.
- After packaging changes, run `mise run image:build` then `mise run image:test`. The latter creates and removes its own container on a random loopback port.

## Local development

- Use `mise run dev`, `mise daemons status/logs`, and `mise run stop` for background servers.
- For port conflicts, follow the `mise.local.toml` example in `README.md`: update environment **and** readiness checks; a local daemon declaration replaces its entire definition.
- Java dependency versions come from the Spring Boot BOM with strict `gradle.lockfile` locking. After dependency changes: `mise exec -- ./gradlew dependencies --write-locks`.
- Keep `gradlew` executable with LF endings; `gradlew.bat` is the Windows wrapper, not a source to translate. Tasks target Linux/macOS or WSL.
- Preserve `.github/workflows/hexlet-check.yml` unchanged; project-owned CI belongs in `.github/workflows/ci.yml`.

## Toolchain

- Before changing tool versions or addressing npm advisories, read `docs/toolchain.md`.
- `mise run audit` is separate from `check`; a passing check is not a clean security audit.
