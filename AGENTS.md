# Repository guidance

## Commit messages

- Use Conventional Commits: `<type>(<scope>): <description>`. Scope is optional; when useful, choose `api`, `backend`, `frontend`, `build`, or `deps`.
- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `style`, `build`, `ci`, `chore`. Use `build(deps)` for dependency updates; `style` means code formatting, not UI styling.
- Write short Russian descriptions starting with an action verb, without a final period: `feat(frontend): добавить выбор времени встречи`.
- Mark incompatible changes with `!` before the colon and explain them in a `BREAKING CHANGE:` footer.
- Keep each commit logically complete.
- Reference the implementation ticket in every implementation commit: `feat(backend): добавить исходный профиль владельца (#14)`.

## Implementation workflow

- Before implementation, read the target GitHub issue, its dependencies, and the applicable sections of the referenced specification. Implement only the current issue's scope.
- Use TDD at the agreed public boundaries: real HTTP API and browser interactions with the real server. For each behavior, write one failing test, confirm it fails for the expected reason, then add the minimal implementation that makes it pass.
- Derive test cases from the specification, including boundary values, invalid data, state conflicts, and the absence of partial changes. Test concurrent behavior with genuinely concurrent requests and time-dependent rules with a controllable clock.
- Frontend calls to the contract API must use the generated SDK.
- API tests must verify the real server's paths, methods, response fields, status codes, and error bodies against the contract and specification.
- Use Playwright for browser tests. Booking scenarios must run against the built frontend served by the real application server, without substituting booking API responses. Keep test state isolated.
- Before completing implementation, run `mise run check`. Report the result and any checks not run. Claim CI success only after a successful GitHub Actions run for the relevant commit.

## Environment and verification

- Run commands from the repository root through mise. Bootstrap with `mise trust` then `mise install`; its postinstall hook runs `npm ci` and installs hk hooks outside CI. `.npmrc` enforces exact Node/npm versions, so system npm may fail even when Node matches.
- Keep `npm:npm` before `node` in `mise.toml`: otherwise Node's bundled npm takes precedence. Ensure the mise executable itself is on `PATH` because tasks invoke mise recursively.
- `mise run check` is the local/CI gate: generated-artifact check → lint/format checks → TypeScript check → JUnit → frontend build. It does **not** build or test the Docker image.
- When introducing full-stack browser checks, include them in `mise run check` so one command runs all verification locally and in CI, including real HTTP API tests and Playwright against the built application with its real server. Update this command's description and CI together; browser installation remains an environment setup step, and Docker image checks remain separate.
- Focused Java test: `mise exec -- ./gradlew --no-daemon test --tests 'com.testproject.calendar.CalendarApplicationTests.applicationRespondsToHttpRequest'`. Tests start their own HTTP server on a random port; no running dev server, Docker, or database is required.
- Frontend-only: `mise run typecheck` or `mise exec -- npm run build --workspace frontend`. The workspace build consumes the existing generated client; it does not regenerate it.
- After packaging changes, run `mise run image:build` then `mise run image:test`. The latter creates and removes its own container on a random loopback port.

## API and packaging boundaries

- The TypeSpec contract is the API source of truth. Change the contract sources, run `mise run api:generate`, and include all affected generated OpenAPI, server models and interfaces, and client SDK artifacts together.
- Generated paths: `api/openapi/` and `frontend/src/api/generated/`. Never edit files in these paths manually; update them through generation. When server generation is introduced, add its exact output path here.
- Keep generated artifacts committed. `mise run api:check` must regenerate every artifact set in a temporary directory and compare both contents and complete file lists, detecting added, removed, and changed files without rewriting the working tree. Keep generator versions pinned and treat TypeSpec warnings as errors.
- When adding or changing server generation, compile application controllers against the generated interfaces and verify compatibility through a real HTTP test; successful generation alone is insufficient.
- There is one npm workspace (`frontend`), with shared tooling and one lockfile at the root. Install dependencies from the root.
- Production is one Spring Boot JAR/image serving the React bundle and `/api` on the same origin. `bootJar` runs codegen/frontend build and embeds `frontend/dist` under `BOOT-INF/classes/static`; `bootRun` does not build or serve the Vite frontend.
- `-PfrontendPrebuilt=true` is for packaging an already-built frontend, as in Docker; it skips generation/build and requires `frontend/dist/index.html` to exist.
- Preserve the routing distinction in `FrontendConfiguration`: extensionless UI routes fall back to `index.html`; missing `/api`, `/actuator`, and asset paths must retain real 404 responses.

## Development and maintenance gotchas

- Use `mise run dev`, `mise daemons status/logs`, and `mise run stop` for background servers. For port conflicts, follow the `mise.local.toml` example in `README.md`: update environment **and** readiness checks; a local daemon declaration replaces its entire definition.
- Java dependency versions come from the Spring Boot BOM with strict `gradle.lockfile` locking. After dependency changes: `mise exec -- ./gradlew dependencies --write-locks`.
- Before changing tool versions or addressing npm advisories, read `docs/toolchain.md` for compatibility constraints and known build-time vulnerabilities. `mise run audit` is separate from `check`; a passing check is not a clean security audit.
- Preserve `.github/workflows/hexlet-check.yml` unchanged; project-owned CI belongs in `.github/workflows/ci.yml`.
- Keep `gradlew` executable with LF endings; `gradlew.bat` is the Windows wrapper, not a source to translate. Tasks target Linux/macOS or WSL.
- Consult `README.md` for product scope before implementing booking features; its development section covers setup and local overrides.

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues. Read `docs/agents/issue-tracker.md` before tracker operations.

### Triage labels

Five canonical triage labels are used. Read `docs/agents/triage-labels.md` before applying triage labels.

### Domain docs

Single-context layout. Before exploring domain concepts or architectural decisions, read `docs/agents/domain.md`.
