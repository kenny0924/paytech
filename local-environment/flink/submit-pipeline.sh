#!/usr/bin/env bash
# Submits one Flink CDC pipeline definition to the analytics Flink cluster (runs in the flink-cdc-submit container).
# Substitutes only the two password variables into a private temporary copy; values are never printed.
# Skips submission when a RUNNING job with the same pipeline name already exists, so re-running is safe.
set -euo pipefail

readonly PIPELINE_FILE="${1:?usage: submit-pipeline.sh <pipeline.yaml>}"
readonly REST=http://flink-jobmanager:8081

for variable_name in MOR_CDC_DB_PASSWORD MOR_DORIS_ROOT_PASSWORD; do
  if [[ ! "${!variable_name:-}" =~ ^[A-Za-z0-9._@%+=:-]{16,128}$ ]]; then
    echo "Invalid local password format for ${variable_name}" >&2
    exit 1
  fi
done

pipeline_name="$(sed -n 's/^  name: *//p' "$PIPELINE_FILE" | tail -1)"
for attempt in $(seq 1 30); do
  if curl -fsS "${REST}/overview" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == 30 ]]; then
    echo "Flink JobManager ${REST} is not reachable" >&2
    exit 1
  fi
  sleep 2
done

if "$FLINK_HOME/bin/flink" list -r 2>/dev/null | grep -q " : ${pipeline_name} (RUNNING)"; then
  echo "Pipeline ${pipeline_name} is already RUNNING; nothing submitted"
  exit 0
fi

rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT
chmod 600 "$rendered"
envsubst '${MOR_CDC_DB_PASSWORD} ${MOR_DORIS_ROOT_PASSWORD}' <"$PIPELINE_FILE" >"$rendered"
"$FLINK_CDC_HOME/bin/flink-cdc.sh" "$rendered" --flink-home "$FLINK_HOME"
