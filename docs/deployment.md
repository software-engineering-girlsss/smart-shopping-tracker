# Deployment Guide

## Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | bundled with Docker Desktop |
| Git | any | for cloning |
| Server RAM | 1 GB+ | 512 MB for Spring Boot + 256 MB for Postgres |
| Server OS | Linux x86-64 | Ubuntu 22.04 LTS recommended |

---

## Local Development (no credentials required)

The app runs in **stub mode** when credentials are absent — it returns sample prices so you can see the full UI without a Picnic account or REWE certificate.

```bash
# 1. Clone
git clone <repo-url>
cd shopping-planer-prototype

# 2. Run in dev mode (H2 in-memory DB, stub data)
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up --build

# 3. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

---

## Production Deployment on a Remote Server

### Step 1 — Prepare the server

```bash
# Install Docker (Ubuntu)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

### Step 2 — Configure credentials

```bash
cd shopping-planer-prototype/docker

# Copy example env file
cp .env.example .env

# Edit with your values
nano .env
```

Required values in `.env`:

| Variable | Description |
|----------|-------------|
| `POSTGRES_PASSWORD` | Strong password for PostgreSQL |
| `PICNIC_EMAIL` | Your Picnic account email |
| `PICNIC_PASSWORD` | Your Picnic account password |
| `OPENAI_API_KEY` | OpenAI key (leave blank for stub mode) |
| `REWE_CERT_FILE` | Path to extracted REWE cert (see below) |
| `REWE_KEY_FILE` | Path to extracted REWE key (see below) |

### Step 3 — REWE mTLS certificate (optional, enables real prices)

REWE's mobile API requires a client certificate from the REWE Android APK:

```bash
# On a machine with Python 3 and an Android device connected via ADB:
pip3 install cryptography
python3 backend/setup-rewe-cert.py   # extracts rewe.pem and rewe.key

# Copy certs to the docker/certs directory on your server
mkdir -p docker/certs
cp rewe.pem rewe.key docker/certs/
```

Without the cert, REWE results will be stub data (product names, no live prices).

### Step 4 — Build and start

```bash
cd docker

# Build the image and start all services
docker compose up --build -d

# Verify health
docker compose ps
curl http://localhost:8080/api/v1/health
```

### Step 5 — Set up a reverse proxy (recommended)

Use Nginx or Caddy in front of port 8080:

**Nginx example** (`/etc/nginx/sites-available/shopping-planer`):
```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/shopping-planer /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

---

## Managing the Running Stack

```bash
# View logs
docker compose -f docker/docker-compose.yml logs -f backend

# Restart backend only
docker compose -f docker/docker-compose.yml restart backend

# Pull latest image and redeploy (after git push)
git pull
docker compose -f docker/docker-compose.yml up --build -d

# Stop everything
docker compose -f docker/docker-compose.yml down

# Stop and remove data volumes (WARNING: deletes all DB data)
docker compose -f docker/docker-compose.yml down -v
```

---

## Environment Variable Reference

All `app.*` properties from `application.yml` can be overridden via environment variables using Spring's relaxed binding (`APP_REWE_ZIP_CODE` → `app.rewe.zipCode`).

| Env Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Use `prod` for PostgreSQL |
| `APP_REWE_SERVICE_TYPE` | `DELIVERY` | `DELIVERY` or `PICKUP` |
| `APP_REWE_ZIP_CODE` | *(blank)* | German postal code |
| `APP_REWE_CERT_FILE` | *(blank)* | Path to rewe.pem |
| `APP_REWE_KEY_FILE` | *(blank)* | Path to rewe.key |
| `APP_PICNIC_EMAIL` | *(blank)* | Picnic account email |
| `APP_PICNIC_PASSWORD` | *(blank)* | Picnic account password |
| `APP_PICNIC_COUNTRY` | `de` | Country code (`de` or `nl`) |
| `APP_OPENAI_API_KEY` | *(blank)* | OpenAI API key |
| `APP_OPENAI_MODEL` | `gpt-4o-mini` | OpenAI model name |

---

## Stub Mode

The app works fully without any credentials — useful for demos and development:

| Missing credential | Behaviour |
|---|---|
| No `APP_REWE_CERT_FILE` | REWE returns 3 hardcoded stub items per query |
| No `APP_PICNIC_EMAIL` | Picnic returns 3 hardcoded stub items per query |
| No `APP_OPENAI_API_KEY` | First candidate is selected instead of AI-matched cheapest |

Stub items are labelled `[stub]` in the response so they're easy to identify.
