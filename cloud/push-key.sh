#!/usr/bin/env bash
# Uploads the Firebase service-account key (chat push notifications) to Cloudflare as the
# FCM_SERVICE_ACCOUNT secret, retrying while Cloudflare's API times out, then checks it is live.
#   ./cloud/push-key.sh ~/Downloads/<project>-firebase-adminsdk-....json
set -uo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/.local/bin:$PATH"

KEY="${1:-}"
[ -f "$KEY" ] || KEY=$(ls -t "$HOME"/Downloads/*firebase-adminsdk*.json 2>/dev/null | head -1)
[ -f "$KEY" ] || { echo "Firebase key file not found. Usage: $0 <key.json>"; exit 1; }
echo "Key: $KEY"

for i in 1 2 3 4 5 6; do
  if npx wrangler secret put FCM_SERVICE_ACCOUNT < "$KEY" 2>&1 | grep -q "Success"; then
    echo "✓ uploaded (try $i)"
    break
  fi
  echo "  Cloudflare did not answer, retry $i/5…"
  sleep 10
  [ "$i" = 6 ] && { echo "✗ could not reach Cloudflare, run again later"; exit 1; }
done

# the chat server reports whether it sees the key now
source <(grep ^KAWAII .secrets)
for i in 1 2 3 4 5 6; do
  sleep 5
  r=$(curl -s -m 30 -H "Authorization: Bearer $KAWAII_UPLOAD_TOKEN" "$KAWAII_BASE_URL/api/chat/status")
  if echo "$r" | grep -q '"hasFirebaseKey":true'; then echo "✓ chat server has the key: $r"; echo "Done. You can delete $KEY now."; exit 0; fi
done
echo "Uploaded, but the server does not report it yet: $r"
