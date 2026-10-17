#!/usr/bin/env bash
set -euo pipefail
environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -f "$environment_dir/.env.test" ]]; then
  echo 'Run test-environment/scripts/prepare.py before Compose.' >&2
  exit 1
fi
for reserved_variable in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG BUILDKIT_HOST; do
  if [[ -n "${!reserved_variable:-}" ]]; then
    echo "Unset $reserved_variable; mor-test Compose only supports the current local Docker context." >&2
    exit 1
  fi
done
# Compose normally lets exported host variables override --env-file. Pin each declared
# test value without sourcing executable shell text or inheriting local-environment secrets.
while IFS='=' read -r variable value || [[ -n "$variable" ]]; do
  [[ -z "$variable" || "$variable" == \#* ]] && continue
  if [[ ! "$variable" =~ ^[A-Z][A-Z0-9_]*$ || ! "$value" =~ ^[A-Za-z0-9_./:@%+=-]+$ ]]; then
    echo 'Invalid literal entry in .env.test; use unquoted generated values.' >&2
    exit 1
  fi
  case "$variable" in
    DOCKER_*|COMPOSE_*|BUILDKIT_*|PATH|HOME|XDG_*|BASH_ENV|ENV|SHELLOPTS|BASHOPTS|CDPATH)
      echo "Reserved environment variable is not allowed in .env.test: $variable" >&2
      exit 1
      ;;
  esac
  export "$variable=$value"
done < "$environment_dir/.env.test"
exec docker compose --project-name mor-test --project-directory "$environment_dir" \
  --env-file "$environment_dir/.env.test" -f "$environment_dir/compose.yaml" \
  -f "$environment_dir/compose.applications.yaml" -f "$environment_dir/compose.scheduler.yaml" \
  --profile base --profile applications --profile scheduler "$@"
