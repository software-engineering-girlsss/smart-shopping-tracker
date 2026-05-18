#!/usr/bin/env bash
# PreToolUse hook: blocks git commit/push when AGENT_ALLOW_COMMITS != "true"
# Receives Bash tool input as JSON on stdin

INPUT=$(cat)
COMMAND=$(printf '%s' "$INPUT" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('command', ''))
except Exception:
    pass
" 2>/dev/null || true)

if printf '%s' "$COMMAND" | grep -qE '\bgit[[:space:]]+(commit|push)\b'; then
    if [[ "${AGENT_ALLOW_COMMITS:-false}" != "true" ]]; then
        printf '\n[guard-commits] Git commits and pushes are DISABLED for this agent session.\n' >&2
        printf 'To allow commits, set AGENT_ALLOW_COMMITS=true in .claude/settings.local.json:\n' >&2
        printf '  { "env": { "AGENT_ALLOW_COMMITS": "true" } }\n\n' >&2
        exit 2
    fi
fi
