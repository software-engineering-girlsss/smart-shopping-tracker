#!/usr/bin/env bash
# Refreshes dynamic sections in CLAUDE.md (between <!-- AUTO:START --> markers)
# Usage:
#   bash .claude/scripts/refresh-context.sh          # standard refresh
#   FULL=1 bash .claude/scripts/refresh-context.sh  # re-scan endpoints too

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
CLAUDE_MD="$ROOT/CLAUDE.md"

if [[ ! -f "$CLAUDE_MD" ]]; then
    echo "CLAUDE.md not found at $CLAUDE_MD — skipping." >&2
    exit 0
fi

TIMESTAMP=$(date -u '+%Y-%m-%d %H:%M UTC')

# ── Helpers ─────────────────────────────────────────────────────────────────

update_section() {
    local name="$1" content="$2"
    python3 - "$CLAUDE_MD" "$name" "$content" <<'PY'
import sys, re
path, name, content = sys.argv[1], sys.argv[2], sys.argv[3]
start = f"<!-- AUTO:START {name} -->"
end   = f"<!-- AUTO:END {name} -->"
text  = open(path).read()
if start not in text:
    sys.exit(0)
new = re.sub(
    re.escape(start) + r'.*?' + re.escape(end),
    f"{start}\n{content}\n{end}",
    text, flags=re.DOTALL
)
open(path, 'w').write(new)
PY
}

# ── Update timestamp ─────────────────────────────────────────────────────────
sed -i "s|_Last refreshed:.*_|_Last refreshed: ${TIMESTAMP}_|g" "$CLAUDE_MD" 2>/dev/null || true

# ── Make targets ─────────────────────────────────────────────────────────────
if [[ -f "$ROOT/Makefile" ]]; then
    TARGETS=$(grep -E '^[a-zA-Z_-]+:.*?## ' "$ROOT/Makefile" | \
        awk -F'## ' '{
            split($1, a, ":");
            gsub(/[[:space:]]*$/, "", a[1]);
            printf "| `make %-20s | %s |\n", a[1] "`", $2
        }')
    if [[ -n "$TARGETS" ]]; then
        HEADER="| Command | Description |\n|---------|-------------|"
        update_section "MAKE_TARGETS" "$(printf '%s\n%s' "$HEADER" "$TARGETS")"
    fi
fi

# ── Controller / test stats ──────────────────────────────────────────────────
CTRL_DIR="$ROOT/backend/src/main/kotlin"
TEST_DIR="$ROOT/backend/src/test"

CTRL_COUNT=0
TEST_COUNT=0
[[ -d "$CTRL_DIR" ]] && CTRL_COUNT=$(find "$CTRL_DIR" -name '*Controller.kt' | wc -l | tr -d ' ')
[[ -d "$TEST_DIR" ]] && TEST_COUNT=$(find "$TEST_DIR" -name '*Test*.kt' | wc -l | tr -d ' ')

update_section "STATS" "- REST controllers: ${CTRL_COUNT}
- Integration test files: ${TEST_COUNT}"

# ── Done ─────────────────────────────────────────────────────────────────────
echo "CLAUDE.md refreshed — ${TIMESTAMP}"
