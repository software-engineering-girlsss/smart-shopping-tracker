# Shopping Planer — Agent Context

> Grocery price aggregator for Germany. Finds the cheapest product basket by comparing REWE and Picnic delivery services in real time.

_Last refreshed: 2026-05-07 15:24 UTC_

---

## Architecture

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin 2.1 + Spring Boot 3.3.6 + Java 21 |
| Frontend | TypeScript + React Native (Expo 54) |
| Database | PostgreSQL 16 (prod) / H2 in-memory (dev) |
| Auth | Supabase — JWT validated by Spring OAuth2 Resource Server |
| Build | Gradle 8.13 + Makefile |
| Container | Docker Compose |
| Deployment | Render.com (backend as Docker service) |
| Task tracking | Trello |

**Monorepo layout:**
```
backend/       Kotlin Spring Boot app
frontend/      Expo React Native app (TypeScript)
docker/        docker-compose + env files
.claude/       Claude Code configuration (settings, hooks, scripts)
```

---

## Local Development

### Prerequisites
- Docker + Docker Compose
- JDK 21 (for running Gradle without Docker)
- Node.js 20+ (for frontend)
- `docker/.env.local` filled with credentials (copy from `docker/.env.local.example`)

### First-time setup

```bash
cp docker/.env.local.example docker/.env.local   # fill in credentials
cp .claude/settings.local.json.example .claude/settings.local.json
make setup                                         # installs git pre-commit hook
```

### Make Targets

<!-- AUTO:START MAKE_TARGETS -->
| Command | Description |
|---------|-------------|
| `make help`                | Show available targets |
| `make start`               | Start backend with local postgres + frontend (Expo on :8081) |
| `make start-remote`        | Start backend with remote DB + frontend (Expo on :8081) |
| `make start-dev`           | Start backend in dev mode (H2, no Docker build wait) + frontend |
| `make backend`             | Start only the backend stack (local postgres + API), detached |
| `make backend-remote`      | Start only the backend connected to remote DB (no local postgres) |
| `make backend-dev`         | Start backend locally via Gradle (H2, dev profile; Redis in Docker) |
| `make frontend`            | Start only the frontend (Expo on :8081) |
| `make stop`                | Stop backend containers (DB data preserved) and expo |
| `make restart`             | Restart backend containers without rebuilding the image |
| `make logs`                | Follow backend logs (Docker) |
| `make logs-dev`            | Follow backend logs (dev mode / bootRun) |
| `make clean`               | Stop containers and wipe the DB volume |
| `make test`                | Run backend integration tests (JUnit 5, H2 in-memory, dev profile) |
| `make refresh-context`     | Refresh dynamic sections of CLAUDE.md from current codebase |
| `make setup`               | Install git pre-commit hook to auto-refresh CLAUDE.md on each commit |
<!-- AUTO:END MAKE_TARGETS -->

### Running Tests

**Always run tests locally before marking a task complete.**

```bash
make test                   # backend integration tests (Spring MockMvc + H2)
```

Tests run against H2 in-memory DB with `spring.profiles.active=dev`.  
Auth: `Authorization: Bearer dev-test-token` (accepted in dev profile).  
H2 console (dev): `http://localhost:8080/h2-console`

### Dev URLs

| URL | Purpose |
|-----|---------|
| `http://localhost:8080/swagger-ui.html` | API docs / try endpoints |
| `http://localhost:8080/api/v1/health` | Health check |
| `http://localhost:8080/h2-console` | DB console (dev profile) |
| `http://localhost:8081` | Expo dev server |

### Frontend Local Override

To point the frontend at a local backend, create `frontend/.env.local`:
```
EXPO_PUBLIC_API_URL=http://localhost:8080/api/v2
```

---

## API Reference

**Production base URL:** `https://api.baskt.me`  
**Auth header:** `Authorization: Bearer <supabase-jwt>`

### V2 Endpoints (current)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v2/health` | Health check |
| `POST` | `/api/v2/auth/login` | Login — proxies to Supabase |
| `POST` | `/api/v2/auth/register` | Register — proxies to Supabase |
| `POST` | `/api/v2/auth/verify-otp` | Verify email OTP |
| `POST` | `/api/v2/auth/resend-code` | Resend OTP |
| `GET` | `/api/v2/products` | Search products across stores |
| `GET` | `/api/v2/products/{id}` | Product detail |
| `GET` | `/api/v2/products/featured` | Featured products |
| `GET` | `/api/v2/cart` | Get user's cart |
| `POST` | `/api/v2/cart/items` | Add item to cart |
| `PATCH` | `/api/v2/cart/items/{id}` | Update cart item |
| `DELETE` | `/api/v2/cart/items/{id}` | Remove cart item |
| `DELETE` | `/api/v2/cart` | Clear cart |
| `GET` | `/api/v2/favorites` | List favorites |
| `POST` | `/api/v2/favorites` | Add favorite |
| `DELETE` | `/api/v2/favorites/{id}` | Remove favorite |
| `GET` | `/api/v2/stores` | List stores |
| `GET` | `/api/v2/tags` | Product tag filters |

### V1 Endpoints (legacy web frontend)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/health` | Health check |
| `POST` | `/api/v1/compare` | Price comparison |
| `POST` | `/api/v1/search` | Product search |

---

## Key Code Locations

| What | Path |
|------|------|
| REST controllers | `backend/src/main/kotlin/.../api/` |
| Business logic | `backend/src/main/kotlin/.../service/` |
| Domain models | `backend/src/main/kotlin/.../model/` |
| Config + typed properties | `backend/src/main/kotlin/.../config/` |
| Security / JWT config | `backend/src/main/kotlin/.../security/` |
| Integration tests | `backend/src/test/kotlin/` |
| Frontend pages (file-based routing) | `frontend/app/` |
| API HTTP client | `frontend/src/api/client.ts` |
| Global state (Zustand) | `frontend/src/store/index.ts` |
| Supabase client | `frontend/src/lib/supabase.ts` |
| Docker config | `docker/docker-compose.yml` |
| Environment template | `docker/.env.local.example` |
| Spring config (base) | `backend/src/main/resources/application.yml` |
| Spring config (prod) | `backend/src/main/resources/application-prod.yml` |
| Spring config (dev) | `backend/src/main/resources/application-dev.yml` |

<!-- AUTO:START STATS -->
- REST controllers: 14
- Integration test files: 5
<!-- AUTO:END STATS -->

---

## External Services

### Supabase (Auth)
- Used for: registration, login, OTP, JWT issuance
- Backend validates Supabase JWTs via HS256 (secret from `APP_SUPABASE_JWT_SECRET`) or JWKS
- Auth proxy endpoints: `/api/v2/auth/*`
- Frontend: `frontend/src/lib/supabase.ts`, OAuth callback at `/auth/callback`

### Render.com (Deployment)
- Backend runs as a Docker service
- Uses `docker-compose.remote-db.yml` override (no local postgres)
- Env vars are set in the Render dashboard (mirrors `docker/.env.local`)
- Dashboard: https://dashboard.render.com

### Trello (Task Tracking)
- Branch naming: `feature/<trello-card-id>-short-description`
- PR titles should reference the Trello card ID

### Third-party APIs
| API | Auth method | Config key prefix |
|-----|------------|-------------------|
| REWE | mTLS (client cert in `.certs/`) | `APP_REWE_*` |
| Picnic | Email + password token | `APP_PICNIC_*` |
| OpenAI | API key (product matching) | `APP_OPENAI_*` |

---

## Agent Rules

### GIT CONTROL — Automatic Branch / Commit / Push

After completing any code change, the agent **always** performs the full git workflow automatically — without waiting to be asked:

1. Create a new branch from the current base (`hotfix/<short-name>` for bug fixes, `feature/<trello-id>-<name>` for features)
2. Run `make test` (backend changes) or verify manually (frontend changes)
3. Stage only the changed files (never `git add -A` blindly)
4. Commit with a conventional message ending with the Co-Authored-By trailer
5. Push the branch to origin

This behaviour is always active. No special keyword is needed.

### Commit Flag

`AGENT_ALLOW_COMMITS` controls whether `git commit` / `git push` are permitted by the pre-commit hook.

- The hook reads `AGENT_ALLOW_COMMITS` from `.claude/settings.local.json`
- **GIT CONTROL is enabled by default** — `.claude/settings.local.json` ships with `AGENT_ALLOW_COMMITS=true`
- To temporarily disable for a session, set it to `"false"` in that file
- `.claude/settings.local.json` is gitignored — each developer keeps their own copy

### Testing Requirement

Never mark a task complete without running tests:
1. Backend changes → `make test` must pass
2. API contract changes → verify with Swagger UI or curl against local server
3. Frontend changes → run `make frontend` and test manually in Expo Go

Integration tests use H2 + dev profile — no real API keys are needed to run them.

### Verify Claims Locally Before Stating Them

Do not assert performance improvements, timing reductions, or behavioral changes without local verification first. Specifically:
- Rebuild the container (`docker compose build --no-cache backend`) if code changed — `make backend` reuses the cached image
- For latency claims, write a test or measure with `curl --write-out "%{time_total}"` against the local server
- For fire-and-forget / async claims, the correct test is a timing assertion with a slow mock, not a log grep

### Context Refresh

After adding or removing API endpoints, services, or Make targets, run:
```bash
make refresh-context
```

This updates the `<!-- AUTO:START ... -->` sections in this file and bumps the timestamp.  
The git pre-commit hook (installed by `make setup`) does this automatically on every commit.

### Code Style

- Kotlin: follow patterns in existing controllers and services
- TypeScript: strict mode, no implicit `any`
- No comments unless the WHY is non-obvious
- No abstractions beyond the task scope
- No backwards-compat shims for removed code — delete it cleanly

---

## Onboarding a New Teammate

```bash
git clone <repo>
cd shopping-planer-prototype
cp docker/.env.local.example docker/.env.local   # get credentials from team
cp .claude/settings.local.json.example .claude/settings.local.json
make setup          # installs git hook
make backend        # start backend
make frontend       # start frontend (separate terminal)
make test           # verify everything works
```

Open `http://localhost:8080/swagger-ui.html` to browse the API.
