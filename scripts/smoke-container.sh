#!/usr/bin/env bash
# Starts only the disposable CI fixture. Requires a locally loaded CI_IMAGE,
# Docker Compose, curl, Python 3, and jq. Never sources a developer .env file.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
export CI_IMAGE="${CI_IMAGE:-capstone-1:ci}"
export CI_APP_PORT="${CI_APP_PORT:-18080}"
export CI_PROMETHEUS_PORT="${CI_PROMETHEUS_PORT:-19090}"
export CI_GRAFANA_PORT="${CI_GRAFANA_PORT:-13000}"
export APP_URL="http://127.0.0.1:${CI_APP_PORT}"
export PROMETHEUS_URL="http://127.0.0.1:${CI_PROMETHEUS_PORT}"
export GRAFANA_URL="http://127.0.0.1:${CI_GRAFANA_PORT}"
# These are fixture-only credentials, unrelated to any deployed or local account.
export METRICS_PASSWORD=disposable-ci-metrics
export GF_SECURITY_ADMIN_USER=ci_admin
export GF_SECURITY_ADMIN_PASSWORD=disposable-ci-grafana

project="${CI_COMPOSE_PROJECT:-campusbridge-smoke-$(date +%s)-$$}"
artifacts="${CI_SMOKE_ARTIFACTS:-$(mktemp -d /tmp/campusbridge-smoke.XXXXXX)}"
mkdir -p "$artifacts"
compose=(docker compose --env-file /dev/null -f docker-compose.ci.yml -p "$project")
if [[ "${CI_MONITORING:-false}" == true ]]; then
  compose+=(--profile monitoring)
fi

cleanup() {
  status=$?
  trap - EXIT INT TERM
  "${compose[@]}" ps --all > "$artifacts/compose-ps.txt" 2>&1 || true
  "${compose[@]}" logs --no-color > "$artifacts/compose.log" 2>&1 || true
  if [[ "$status" -ne 0 ]]; then
    cat "$artifacts/compose-ps.txt" >&2
    tail -n 200 "$artifacts/compose.log" >&2
  fi
  # The unique project contains only this script's disposable fixture resources.
  if ! "${compose[@]}" down --volumes --remove-orphans; then
    status=1
  fi
  echo "Container smoke diagnostics: $artifacts"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"${compose[@]}" config --quiet
expected_image="$(docker image inspect "$CI_IMAGE" --format '{{.Id}}')"
"${compose[@]}" up -d --no-build

healthy=false
for ((attempt=1; attempt<=90; attempt++)); do
  if curl --silent --show-error --fail --max-time 3 "$APP_URL/actuator/health" \
      -o "$artifacts/health.json" 2>/dev/null \
      && jq -e '.status == "UP"' "$artifacts/health.json" >/dev/null; then
    healthy=true
    break
  fi
  sleep 2
done
if [[ "$healthy" != true ]]; then
  echo "Container health never became UP within 180 seconds." >&2
  exit 1
fi

container_id="$("${compose[@]}" ps -q app)"
test "$(docker inspect "$container_id" --format '{{.Image}}')" = "$expected_image"
test "$(docker inspect "$container_id" --format '{{.RestartCount}}')" = 0
"${compose[@]}" exec -T app java -version > "$artifacts/java-version.txt" 2>&1
grep -Eq 'version "21[.]' "$artifacts/java-version.txt"

curl --silent --show-error --fail --max-time 10 "$APP_URL/" -o "$artifacts/index.html"
python3 - "$artifacts/index.html" > "$artifacts/assets.txt" <<'PY'
from html.parser import HTMLParser
from pathlib import Path
import sys

class Assets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.root = False
        self.js = []
        self.css = []

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        self.root |= attrs.get("id") == "root"
        if tag == "script" and attrs.get("type") == "module":
            self.js.append(attrs.get("src", ""))
        if tag == "link" and attrs.get("rel") == "stylesheet":
            href = attrs.get("href", "")
            if href.startswith("/assets/"):
                self.css.append(href)

page = Assets()
page.feed(Path(sys.argv[1]).read_text())
assert page.root and page.js and page.css, "The packaged SPA or generated assets are missing"
for path in page.js + page.css:
    assert path.startswith("/assets/") and ".." not in path, "Expected a compiled local asset"
    print(path)
PY

while IFS= read -r asset; do
  content_type="$(curl --silent --show-error --fail --max-time 10 \
    "$APP_URL$asset" -o "$artifacts/$(basename "$asset")" -w '%{content_type}')"
  case "$asset:$content_type" in
    *.js:*javascript*|*.css:text/css*) ;;
    *) echo "Incorrect content type for SPA asset: $asset ($content_type)" >&2; exit 1 ;;
  esac
done < "$artifacts/assets.txt"

for route in marketplace messages community support; do
  curl --silent --show-error --fail --max-time 10 "$APP_URL/$route" \
    -o "$artifacts/$route.html"
  cmp "$artifacts/index.html" "$artifacts/$route.html"
done
test "$(curl --silent --show-error --max-time 10 -o /dev/null -w '%{http_code}' \
  "$APP_URL/api/schools")" = 401

if [[ "${CI_MONITORING:-false}" == true ]]; then
  bash scripts/smoke-monitoring.sh
fi
echo "Container smoke passed: Java 21, production PostgreSQL/Flyway startup, health, SPA/assets/routes, and API authentication."
