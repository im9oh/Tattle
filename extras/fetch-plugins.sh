#!/usr/bin/env bash
# Fetch third-party plugin jars from Modrinth into dist/plugins/.
# Runs in CI (full network); the deploy environment can then pull them over git.
set -uo pipefail
mkdir -p dist/plugins

pick_url() {
  python3 -c "
import json, sys
versions = json.load(sys.stdin)
wanted = {'paper', 'spigot', 'bukkit', 'purpur', 'folia'}
for v in versions:
    if not wanted & set(v.get('loaders', [])):
        continue
    files = v.get('files', [])
    primary = next((f for f in files if f.get('primary')), files[0] if files else None)
    if primary and primary['filename'].endswith('.jar'):
        print(primary['url'])
        break
"
}

fetch() {
  local name="$1"; shift
  for slug in "$@"; do
    echo "resolving ${name} via modrinth slug '${slug}'..."
    local url
    url=$(curl -sf "https://api.modrinth.com/v2/project/${slug}/version" | pick_url)
    if [ -n "${url:-}" ]; then
      echo "downloading ${name}: ${url}"
      if curl -sfL "${url}" -o "dist/plugins/${name}.jar"; then
        return 0
      fi
    fi
  done
  echo "ERROR: could not fetch ${name} from any slug" >&2
  return 1
}

status=0
fetch GriefPrevention griefprevention grief-prevention || status=1
fetch SilkSpawners silkspawners silk-spawners silkspawnersv2 || status=1
fetch AuctionHouse auction-house auctionhouse || status=1
ls -la dist/plugins
exit ${status}
