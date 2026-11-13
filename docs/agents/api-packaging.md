# API and packaging

## Contract and generation

- The TypeSpec contract is the API source of truth. Change the contract sources, run `mise run api:generate`, and include all affected generated OpenAPI, server models and interfaces, and client SDK artifacts together.
- Generated paths: `api/openapi/`, `api/server/` and `frontend/src/api/generated/`. Never edit files in these paths manually; update them through generation.
- Keep generated artifacts committed. `mise run api:check` must regenerate every artifact set in a temporary directory and compare both contents and complete file lists, detecting added, removed, and changed files without rewriting the working tree. Keep generator versions pinned and treat TypeSpec warnings as errors.
- `api:check` must not rewrite the working tree; keep a regression test for added, removed, and changed files and for unchanged inputs.
- When adding or changing server generation, compile application controllers against the generated interfaces and verify compatibility through a real HTTP test; successful generation alone is insufficient.

## Packaging

- There is one npm workspace (`frontend`), with shared tooling and one lockfile at the root. Install dependencies from the root.
- Production is one Spring Boot JAR/image serving the React bundle and `/api` on the same origin. `bootJar` runs codegen/frontend build and embeds `frontend/dist` under `BOOT-INF/classes/static`; `bootRun` does not build or serve the Vite frontend.
- `-PfrontendPrebuilt=true` is for packaging an already-built frontend, as in Docker; it skips generation/build and requires `frontend/dist/index.html` to exist.
- Preserve the routing distinction in `FrontendConfiguration`: extensionless UI routes fall back to `index.html`; missing `/api`, `/actuator`, and asset paths must retain real 404 responses.
