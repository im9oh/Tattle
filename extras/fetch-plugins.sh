#!/usr/bin/env bash
# Fetch third-party plugin jars into dist/plugins/.
# Runs in CI (full network); the deploy environment then pulls them over git.
# Sources per plugin, tried in order:
#   modrinth:<slug>    exact Modrinth project
#   search:<query>     Modrinth search, best plugin hit
#   spiget:<id>        SpigotMC resource via spiget.org
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

modrinth_slug_url() {
  curl -sf "https://api.modrinth.com/v2/project/$1/version" | pick_url
}

search_slug() {
  curl -sf "https://api.modrinth.com/v2/search?query=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1")&limit=5" \
    | python3 -c "
import json, sys
hits = json.load(sys.stdin).get('hits', [])
for h in hits:
    if h.get('project_type') in (None, 'plugin', 'mod'):
        print(h['slug'])
        break
"
}

fetch() {
  local name="$1"; shift
  for source in "$@"; do
    local kind="${source%%:*}" arg="${source#*:}" url=""
    echo "trying ${name} via ${kind}:${arg}..."
    case "${kind}" in
      modrinth) url=$(modrinth_slug_url "${arg}") ;;
      search)
        local slug
        slug=$(search_slug "${arg}")
        [ -n "${slug}" ] && echo "  search matched slug '${slug}'" && url=$(modrinth_slug_url "${slug}")
        ;;
      spiget) url="https://api.spiget.org/v2/resources/${arg}/download" ;;
    esac
    if [ -n "${url:-}" ]; then
      echo "  downloading: ${url}"
      if curl -sfL -A "GeekedSMP-deploy/1.0" "${url}" -o "dist/plugins/${name}.jar" \
         && [ "$(stat -c%s "dist/plugins/${name}.jar")" -gt 10000 ]; then
        echo "  OK ($(stat -c%s "dist/plugins/${name}.jar") bytes)"
        return 0
      fi
      rm -f "dist/plugins/${name}.jar"
    fi
  done
  echo "ERROR: could not fetch ${name} from any source" >&2
  return 1
}

status=0
fetch GriefPrevention modrinth:griefprevention || status=1
fetch SilkSpawners modrinth:silkspawners modrinth:silk-spawners || status=1
fetch AuctionHouse modrinth:auction-house modrinth:auctionhouse "search:auction house" spiget:60325 || status=1
ls -la dist/plugins
exit ${status}
