# Shopping Planer

> Find the cheapest grocery basket across **REWE** and **Picnic** (German delivery services).

## Quick Start (5 minutes, no credentials needed)

The app runs in **stub mode** without any API keys — perfect for demos and development.

```bash
# 1. Clone
git clone <repo-url> && cd shopping-planer-prototype

# 2. Start backend (H2 in-memory DB, stub prices)
java -jar backend/build/libs/backend-*.jar --spring.profiles.active=dev

# OR build first if you haven't yet:
./gradlew :backend:bootJar
java -jar backend/build/libs/backend-*.jar --spring.profiles.active=dev

# 3. Try the API
curl http://localhost:8080/api/v1/health
curl -X POST http://localhost:8080/api/v1/compare \
  -H 'Content-Type: application/json' \
  -d '{"products":[{"name":"Milch","quantity":2,"unit":"liter"},{"name":"Butter","quantity":1,"unit":"stk"}]}'

# 4. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Local PROD mode (2 commands)

Run both backend and frontend locally against the real DB and real auth.

**Prerequisites:** Docker, Node.js ≥ 18, `make`

```bash
# 1. Prepare credentials (copy from render.com env vars — do once)
cp docker/.env.local.example docker/.env.local
# → fill in SPRING_DATASOURCE_*, APP_PICNIC_*, APP_REWE_*, APP_SUPABASE_* etc.

# 2. Start everything
make start
```

`make start` builds and starts the backend in Docker (detached), then launches the Expo dev server on port 8081. When you're done, Ctrl+C stops the frontend. To stop the backend:

```bash
make stop        # stops containers, keeps DB volume
make clean       # stops containers AND wipes DB volume
make logs        # follow backend logs
make backend     # restart only the backend
make frontend    # restart only the frontend
```

Both `make start` and `make backend` rebuild the backend image automatically if source has changed. The `.certs/` directory at the project root is mounted into the container automatically — no manual path changes needed.

---

## Local Docker (no Compose, PostgreSQL)

Run the full stack locally using plain `docker` commands. The backend binds to `127.0.0.1:8080` only — not reachable from outside the machine.

```bash
# 1. Prepare env file (edit credentials if needed, or leave empty for stub mode)
cp docker/.env.local.example docker/.env.local

# 2. Shared network so containers can talk to each other
docker network create shopping-net

# 3. PostgreSQL (data survives container restarts via named volume)
docker run -d \
  --name shopping-postgres \
  --network shopping-net \
  -e POSTGRES_DB=shoppingplaner \
  -e POSTGRES_USER=planer \
  -e POSTGRES_PASSWORD=localpass \
  -v shopping-pgdata:/var/lib/postgresql/data \
  postgres:16-alpine

# 4. Build backend image (run from project root)
docker build -t shopping-planer-backend -f backend/Dockerfile .

# 5. Run backend — credentials from .env.local, localhost only
docker run -d \
  --name shopping-backend \
  --network shopping-net \
  -p 127.0.0.1:8080:8080 \
  --env-file docker/.env.local \
  shopping-planer-backend

# 6. Check it's up
curl http://localhost:8080/api/v1/health
```

To stop and clean up:
```bash
docker rm -f shopping-backend shopping-postgres
docker network rm shopping-net
# optionally remove DB volume:
docker volume rm shopping-pgdata
```

For real prices, fill in `docker/.env.local` with your Picnic/REWE/OpenAI credentials and add a certs volume:
```bash
docker run -d --name shopping-backend --network shopping-net \
  -p 127.0.0.1:8080:8080 \
  --env-file docker/.env.local \
  -v /path/to/your/certs:/certs:ro \
  shopping-planer-backend
```

## Docker Compose (production)

```bash
cd docker
cp .env.example .env   # edit with your credentials
docker compose up --build -d
```

See [`docs/deployment.md`](docs/deployment.md) for full production setup including REWE cert extraction and remote server deployment.

---

## Project Structure

```
shopping-planer-prototype/
├── backend/               # Kotlin + Spring Boot REST API
│   ├── src/main/kotlin/com/shoppingplaner/
│   │   ├── api/           # REST controllers
│   │   ├── service/       # REWE, Picnic, AI matching, comparison
│   │   ├── model/         # JPA entities + domain models
│   │   ├── repository/    # Spring Data JPA
│   │   ├── dto/           # Request/response DTOs
│   │   └── config/        # Spring configuration
│   └── Dockerfile
├── mobile/                # Kotlin Multiplatform (Android + iOS)
│   ├── shared/            # Shared API client, repositories, ViewModels
│   └── androidApp/        # Android Compose UI
├── docker/                # Docker Compose + env template
├── docs/                  # deployment.md, api-spec
├── drafts/mmvp-rewe-picnic/  # Original CLI reference (read-only)
└── notes/rewe_picnic_architecture.json
```

## API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/v1/health` | Service health check |
| `POST` | `/api/v1/search` | Raw product search (no AI) |
| `POST` | `/api/v1/compare` | Full comparison with AI matching |
| `GET`  | `/api/v1/carts` | List saved carts |
| `POST` | `/api/v1/carts` | Create cart |
| `POST` | `/api/v1/carts/{id}/compare` | Compare prices for a cart |

Full spec: [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html)

## Stub Mode

No credentials? No problem. The app returns realistic stub data when:

| Missing | Behaviour |
|---------|-----------|
| `APP_REWE_CERT_FILE` | REWE returns sample products labelled `[stub]` |
| `APP_PICNIC_EMAIL` | Picnic returns sample products labelled `[stub]` |
| `APP_OPENAI_API_KEY` | First candidate selected instead of AI-matched cheapest |

## Tech Stack

- **Backend:** Kotlin 2.1 · Spring Boot 3.3 · JVM 21 · JPA/Hibernate · H2/PostgreSQL
- **Mobile:** Kotlin Multiplatform · Compose for Android · Ktor HTTP client
- **Infrastructure:** Docker · Docker Compose · PostgreSQL 16
- **AI matching:** OpenAI `gpt-4o-mini` (optional)

## Real Prices Setup

For live prices you need:

1. **Picnic account** — register at [picnic.app](https://picnic.app) (Germany/Netherlands)
2. **REWE mTLS cert** — extracted from the REWE APK:
   ```bash
   pip3 install cryptography
   python3 drafts/mmvp-rewe-picnic/setup-rewe-cert.py
   ```
3. **OpenAI API key** — free at [platform.openai.com](https://platform.openai.com/api-keys)

Then set these in `docker/.env` and restart.
