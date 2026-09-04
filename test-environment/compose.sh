#!/usr/bin/env bash
set -euo pipefail
environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -f "$environment_dir/.env.test" ]]; then
  echo 'Run test-environment/scripts/prepare.py before Compose.' >&2
  exit 1
fi
# Compose normally lets exported host variables override --env-file. Pin each declared
# test value without sourcing executable shell text or inheriting local-environment secrets.
while IFS='=' read -r variable value || [[ -n "$variable" ]]; do
  [[ -z "$variable" || "$variable" == \#* ]] && continue
  if [[ ! "$variable" =~ ^[A-Z][A-Z0-9_]*$ || ! "$value" =~ ^[A-Za-z0-9_./:@%+=-]+$ ]]; then
    echo 'Invalid literal entry in .env.test; use unquoted generated values.' >&2
    exit 1
  fi
  export "$variable=$value"
done < "$environment_dir/.env.test"
exec docker compose --project-name mor-test --project-directory "$environment_dir" \
  --env-file "$environment_dir/.env.test" -f "$environment_dir/compose.yaml" \
  -f "$environment_dir/compose.applications.yaml" --profile base --profile applications "$@"
