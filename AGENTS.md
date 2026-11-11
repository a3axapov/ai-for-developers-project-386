# Repository guidance

## Commit messages

- Use Conventional Commits: `<type>(<scope>): <description>`. Scope is optional; when useful, choose `api`, `backend`, `frontend`, `build`, or `deps`.
- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `style`, `build`, `ci`, `chore`. Use `build(deps)` for dependency updates; `style` means code formatting, not UI styling.
- Write short Russian descriptions starting with an action verb, without a final period: `feat(frontend): добавить выбор времени встречи`.
- Mark incompatible changes with `!` before the colon and explain them in a `BREAKING CHANGE:` footer.
- Keep each commit logically complete; include the contract and its regenerated OpenAPI/client outputs together when the API changes.

## Environment and verification

- Run commands from the repository root through mise. Bootstrap with `mise trust` then `mise install`; its postinstall hook runs `npm ci` and installs hk hooks outside CI. `.npmrc` enforces exact Node/npm versions, so system npm may fail even when Node matches.
- Keep `npm:npm` before `node` in `mise.toml`: otherwise Node's bundled npm takes precedence. Ensure the mise executable itself is on `PATH` because tasks invoke mise recursively.
- `mise run check` is the local/CI gate: generated-artifact check → lint/format checks → TypeScript check → JUnit → frontend build. It does **not** build or test the Docker image.
- Focused Java test: `mise exec -- ./gradlew --no-daemon test --tests 'com.testproject.calendar.CalendarApplicationTests.applicationRespondsToHttpRequest'`. Tests start their own HTTP server on a random port; no running dev server, Docker, or database is required.
- Frontend-only: `mise run typecheck` or `mise exec -- npm run build --workspace frontend`. The workspace build consumes the existing generated client; it does not regenerate it.
- After packaging changes, run `mise run image:build` then `mise run image:test`. The latter creates and removes its own container on a random loopback port.

## API and packaging boundaries

- API source of truth: `api/main.tsp` → `api/openapi/openapi.yaml` → `frontend/src/api/generated/`. Change TypeSpec, run `mise run api:generate`, and include both generated outputs. API clients are generated, not handwritten.
- `mise run api:check` regenerates in a temporary directory and compares bytes/file lists without rewriting the working tree. Generation treats TypeSpec warnings as errors.
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
