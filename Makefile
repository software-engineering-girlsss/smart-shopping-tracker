ENV_FILE      = docker/.env.local
COMPOSE       = docker compose -f docker/docker-compose.yml --env-file $(ENV_FILE)
COMPOSE_DEV   = docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml --env-file $(ENV_FILE)
COMPOSE_REMOTE = docker compose -f docker/docker-compose.yml -f docker/docker-compose.remote-db.yml --env-file $(ENV_FILE)
COMPOSE_ENV   = CERTS_DIR=$(CURDIR)/.certs APP_REWE_CERT_FILE=/certs/rewe.pem APP_REWE_KEY_FILE=/certs/rewe.key

.PHONY: start start-remote start-dev stop restart backend backend-remote backend-dev frontend logs logs-dev clean help test refresh-context setup

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-16s %s\n", $$1, $$2}'

start: backend ## Start backend with local postgres + frontend (Expo on :8081)
	@fuser -k 8081/tcp 2>/dev/null || true
	cd frontend && npm install --silent && npx expo start --port 8081

start-remote: backend-remote ## Start backend with remote DB + frontend (Expo on :8081)
	@fuser -k 8081/tcp 2>/dev/null || true
	cd frontend && npm install --silent && npx expo start --port 8081

start-dev: check-env ## Start backend in dev mode (H2, no Docker build wait) + frontend
	@fuser -k 8081/tcp 2>/dev/null || true
	@fuser -k 8080/tcp 2>/dev/null || true
	@docker run -d --rm --name baskt-redis-dev -p 6379:6379 redis:7-alpine \
		redis-server --save "" --appendonly no 2>/dev/null || true
	@echo "Backend starting in background (H2 dev mode). Logs: make logs-dev"
	@SPRING_PROFILES_ACTIVE=dev REDIS_URL=redis://localhost:6379 \
		./gradlew :backend:bootRun --no-daemon > /tmp/baskt-backend-dev.log 2>&1 &
	cd frontend && npm install --silent && npx expo start --port 8081

backend: check-env ## Start only the backend stack (local postgres + API), detached
	$(COMPOSE_ENV) $(COMPOSE) up --build -d

backend-remote: check-env ## Start only the backend connected to remote DB (no local postgres)
	$(COMPOSE_ENV) $(COMPOSE_REMOTE) up --build -d backend

backend-dev: check-env ## Start backend locally via Gradle (H2, dev profile; Redis in Docker)
	@docker run -d --rm --name baskt-redis-dev -p 6379:6379 redis:7-alpine \
		redis-server --save "" --appendonly no 2>/dev/null || true
	SPRING_PROFILES_ACTIVE=dev REDIS_URL=redis://localhost:6379 \
		./gradlew :backend:bootRun --no-daemon

frontend: ## Start only the frontend (Expo on :8081)
	@fuser -k 8081/tcp 2>/dev/null || true
	cd frontend && npm install --silent && npx expo start --port 8081

stop: ## Stop backend containers (DB data preserved) and expo
	$(COMPOSE) down
	@fuser -k 8081/tcp 2>/dev/null || true
	@fuser -k 8080/tcp 2>/dev/null || true
	@docker rm -f baskt-redis-dev 2>/dev/null || true

restart: check-env ## Restart backend containers without rebuilding the image
	$(COMPOSE) down
	$(COMPOSE_ENV) $(COMPOSE) up -d

logs: ## Follow backend logs (Docker)
	$(COMPOSE) logs -f backend

logs-dev: ## Follow backend logs (dev mode / bootRun)
	@tail -f /tmp/baskt-backend-dev.log

clean: ## Stop containers and wipe the DB volume
	$(COMPOSE) down -v

test: ## Run backend integration tests (JUnit 5, H2 in-memory, dev profile)
	./gradlew :backend:test --no-daemon

refresh-context: ## Refresh dynamic sections of CLAUDE.md from current codebase
	@bash .claude/scripts/refresh-context.sh

setup: ## Install git pre-commit hook to auto-refresh CLAUDE.md on each commit
	@mkdir -p .git/hooks
	@printf '#!/usr/bin/env bash\nbash .claude/scripts/refresh-context.sh\ngit add CLAUDE.md 2>/dev/null || true\n' > .git/hooks/pre-commit
	@chmod +x .git/hooks/pre-commit
	@echo "Pre-commit hook installed."
	@test -f .claude/settings.local.json || { \
		cp .claude/settings.local.json.example .claude/settings.local.json; \
		echo "Created .claude/settings.local.json from example."; \
	}

check-env:
	@test -f $(ENV_FILE) || { \
		echo ""; \
		echo "  Missing $(ENV_FILE)."; \
		echo "  Copy docker/.env.local.example, fill in credentials from render.com, then run make start again."; \
		echo ""; \
		exit 1; \
	}
