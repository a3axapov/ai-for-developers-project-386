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
