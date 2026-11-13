# Repository guidance

## Project map

- `api/main.tsp` — TypeSpec contract (source of truth).
- `api/openapi/`, `api/server/`, `frontend/src/api/generated/` — generated; do not edit.
- `src/main/java/` — Spring Boot app; `src/test/java/` — JUnit tests; `frontend/src/` — React app.

## Commit messages

- Use Conventional Commits: `<type>(<scope>): <description>`; scope is optional (`api`, `backend`, `frontend`, `build`, `deps`).
- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `style`, `build`, `ci`, `chore`; `build(deps)` for dependency updates, `style` for formatting.
- Write short Russian descriptions starting with an action verb, without a final period: `feat(frontend): добавить выбор времени встречи`.
- Mark incompatible changes with `!` before the colon and explain them in a `BREAKING CHANGE:` footer.
- Keep each commit logically complete; reference the implementation ticket: `feat(backend): добавить исходный профиль владельца (#14)`.

## Implementation workflow

- Read the target GitHub issue, its dependencies, and the applicable spec sections. Implement only the current issue's scope.
- Work TDD at the public boundaries: real HTTP API and browser interactions with the real server. For each behavior, write one failing test, confirm it fails for the expected reason, then add the minimal implementation.
- Cover boundary values, invalid data, state conflicts, and the absence of partial changes. Test concurrency with genuinely concurrent requests and time-dependent rules with a controllable clock.
- Frontend calls to the contract API must use the generated SDK.
- API tests must verify the real server's paths, methods, response fields, status codes, and error bodies against the contract and specification.
- Use Playwright for browser tests against the built frontend served by the real application server, without substituting booking API responses. Keep test state isolated.
- Before finishing, run `mise run check`; report the result and any checks not run. Claim CI success only after a green GitHub Actions run for the commit.
- During TDD and review fixes, run focused tests; use `mise run check:fast` for the non-browser suite. Neither replaces the full gate. Run the full gate after final review fixes on the integration tree; reuse its result only while the checked code and configuration remain unchanged.
- Implementers and reviewers do not push. The coordinator pushes the integration branch once per ticket after review, integration and the full gate, then waits for CI once. If CI finds a defect, fix and recheck before a corrective push. Pre-push runs only `check:fast`; it does not certify the full gate.
- Run long checks (`mise run check`, Gradle, Playwright) with an explicit timeout and sequentially; use background only for independent work.
- Put final acceptance evidence in the ticket comment: exact SHA, commands and results, CI links, and review verdict. Do not rely on `/tmp`.
- For push → CI, prefer one waiting run with a sufficient timeout and a single final answer over repeated polling.

## Search and context

- Start API work from `api/main.tsp` and application code. Open generated files only for a specific signature or generation diagnosis.
- Do not search or read by default; narrow with `path` or `include` when the task is about them:
  - `node_modules/`, `.gradle/`, `build/`, `frontend/dist/`;
  - `api/openapi/`, `api/server/**/generated/`, `frontend/src/api/generated/`;
  - `package-lock.json`, `gradle.lockfile`.
- Do not add generated paths to `.gitignore`; they are committed artifacts. Access is not forbidden, only the default search scope.
- Keep tool output small: filter large commands and read files in bounded ranges.
- Fetch tickets in two steps: first numbers and titles, then only the needed issue and specification. Do not request all open tickets at once.

## Verification

- `mise run check` is the single local/CI gate: generated-artifact check → lint/format → typecheck → JUnit including real HTTP API tests → build with the frontend → Playwright against real Spring Boot servers.
- Install browser dependencies once with `mise run test:browser:install`.
- If browser tests fail because system libraries are missing and sudo is unavailable, stop and ask the user to run the documented install command; do not invent workarounds or modify the system.
- Read `docs/agents/environment.md` for focused Java, frontend-only, and image commands, plus setup, ports, and dev servers.

## API and packaging

- The TypeSpec contract is the source of truth. Change the contract, run `mise run api:generate`, and commit all generated OpenAPI, server, and client SDK artifacts together.
- Generated paths: `api/openapi/`, `api/server/`, `frontend/src/api/generated/`. Never edit them manually.
- Keep generated artifacts committed; `mise run api:check` must pass.
- Production is one Spring Boot JAR/image serving the React bundle and `/api` on the same origin.
- Read `docs/agents/api-packaging.md` before changing generation, controllers, packaging, or routing.

## Environment and maintenance

- Run commands from the repository root through mise. Bootstrap with `mise trust` then `mise install`.
- Read `docs/agents/environment.md` for tool versions, npm/mise ordering, ports, locks, CI, and browser setup.
- Read `docs/toolchain.md` before changing tool versions or addressing npm advisories.
- `mise run audit` is separate from `check`; a passing check is not a clean security audit.
- Read `README.md` for product scope before implementing booking features.

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues. Read `docs/agents/issue-tracker.md` before tracker operations.

### Triage labels

Five canonical triage labels are used. Read `docs/agents/triage-labels.md` before applying triage labels.

### Domain docs

Single-context layout. Before exploring domain concepts or architectural decisions, read `docs/agents/domain.md`.
