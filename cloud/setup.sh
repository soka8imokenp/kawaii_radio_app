#!/usr/bin/env bash
# One-time setup of the Kawaii Radio cloud (run after `npx wrangler login`):
# R2 bucket → upload token → Worker deploy → default cover → app .env + .secrets for the agent.
# Safe to re-run: the existing token in .secrets is kept.
set -euo pipefail
cd "$(dirname "$0")"

BUCKET=kawaii-radio
W="npx wrangler"

$W whoami >/dev/null || { echo "Run: npx wrangler login"; exit 1; }

echo "== R2 bucket"
$W r2 bucket list 2>/dev/null | grep -q "name:\s*$BUCKET$" || $W r2 bucket create "$BUCKET"

echo "== upload token"
if [ -f .secrets ] && grep -q '^KAWAII_UPLOAD_TOKEN=' .secrets; then
  TOKEN=$(grep '^KAWAII_UPLOAD_TOKEN=' .secrets | cut -d= -f2-)
else
  TOKEN=$(node -e "console.log(require('crypto').randomBytes(24).toString('base64url'))")
fi

echo "== deploy"
OUT=$($W deploy 2>&1 | tee /dev/stderr)
URL=$(echo "$OUT" | grep -oE 'https://[a-z0-9.-]+\.workers\.dev' | head -1)
[ -n "$URL" ] || { echo "Could not find the workers.dev URL in the deploy output"; exit 1; }
printf '%s' "$TOKEN" | $W secret put UPLOAD_TOKEN

echo "== default cover"
$W r2 object put "$BUCKET/covers/default.webp" --file default-cover.webp --content-type image/webp --remote

cat > .secrets <<EOF
# Kawaii Radio cloud: give these two values to the Telegram agent (see AGENT.md). Never commit this file.
KAWAII_BASE_URL=$URL
KAWAII_UPLOAD_TOKEN=$TOKEN
EOF
echo "VITE_CLOUD_URL=$URL" > ../.env

echo
echo "Done: $URL"
curl -s "$URL/" ; echo
