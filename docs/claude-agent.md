# Using Claude Code with This Project

Claude Code is an AI coding agent that understands this codebase and helps with implementation, debugging, and code review. This guide covers everything you need to start using it.

---

## Prerequisites

- [Claude Code CLI](https://claude.ai/code) installed: `npm install -g @anthropic-ai/claude-code`
- Anthropic account with Claude Code access

---

## First-time Setup

Run these once after cloning:

```bash
cp docker/.env.local.example docker/.env.local       # fill in credentials

# don't forget to add REWE certificate and key to .certs/

cp .claude/settings.local.json.example .claude/settings.local.json
make setup                                             # installs git pre-commit hook
```

`settings.local.json` is gitignored — it's your personal configuration.

---

## Starting the Agent

From the project root:

```bash
claude
```

Claude loads `CLAUDE.md` automatically at the start of every session. It already knows the architecture, API endpoints, make targets, and project rules — you don't need to explain the project each time.

---

## Commit Flag

By default the agent **will not commit or push** anything. This is enforced by a hook, not just a guideline — `git commit` is blocked at the tool level.

To allow the agent to commit in your sessions, edit `.claude/settings.local.json`:

```json
{
  "env": {
    "AGENT_ALLOW_COMMITS": "true"
  }
}
```

To keep commits disabled (default — agent stages changes, you review and commit manually):

```json
{
  "env": {
    "AGENT_ALLOW_COMMITS": "false"
  }
}
```

Restart `claude` after changing this file.

---

## What the Agent Knows

Everything in `CLAUDE.md` is loaded at session start:

- Full tech stack and architecture
- All `make` targets and what they do
- All API endpoints (v1 + v2)
- Where to find controllers, services, models, tests
- How to run and test locally
- Supabase auth, Render.com deployment, Trello conventions
- The commit flag rule and testing requirement

---

## Testing Rule

The agent is instructed to run `make test` before marking any backend task complete. If it tries to skip this, tell it to run the tests first. Tests use H2 in-memory DB — no real credentials needed:

```bash
make test
```

---

## Keeping Context Fresh

When the codebase changes significantly (new endpoints, new services, new make targets), update `CLAUDE.md`:

```bash
make refresh-context
```

This is also run automatically by the git pre-commit hook installed by `make setup`, so in most cases it stays current without manual effort.

---

## Practical Tips

**Be specific about scope.** The agent works best with concrete tasks:
> "Add a `GET /api/v2/users/me` endpoint that returns the current user's profile from the users table"

rather than:
> "Improve the user API"

**Reference Trello cards.** When starting a task tied to a card:
> "Implement Trello card #42 — receipt OCR upload endpoint"

The agent will use the branch naming convention `feature/<card-id>-description`.

**Ask it to explain before changing.** For unfamiliar areas:
> "Explain how Picnic authentication works in this codebase before making any changes"

**Use Swagger for quick endpoint verification.** After the agent adds or changes an endpoint, run `make backend` and open `http://localhost:8080/swagger-ui.html` to test it directly.

---

## Useful Commands During a Session

| What you want | What to say |
|---------------|-------------|
| Run tests | `make test` or ask the agent directly |
| See what changed | `git diff` |
| Start fresh local env | `make clean && make backend` |
| Check backend logs | `make logs` |
| Refresh agent context | `make refresh-context` |

---

## Files Owned by Claude Code

| File | Purpose | Committed? |
|------|---------|-----------|
| `CLAUDE.md` | Agent context loaded every session | Yes |
| `.claude/settings.json` | Team permissions + commit guard hook | Yes |
| `.claude/settings.local.json` | Your personal overrides (commit flag) | No — gitignored |
| `.claude/settings.local.json.example` | Template for the above | Yes |
| `.claude/scripts/guard-commits.sh` | Enforces commit flag at hook level | Yes |
| `.claude/scripts/refresh-context.sh` | Updates CLAUDE.md dynamic sections | Yes |
