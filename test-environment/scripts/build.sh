#!/usr/bin/env bash
set -euo pipefail

prepare_health_probe_output() {
  local requested_environment_dir="$1"
  if [[ -L "$requested_environment_dir" || ! -d "$requested_environment_dir" ]]; then
    echo "Refusing an unexpected test-environment directory" >&2
    return 1
  fi

  local physical_environment_dir
  physical_environment_dir="$(cd -- "$requested_environment_dir" && pwd -P)"
  local artifacts_dir="$physical_environment_dir/artifacts"
  if [[ -L "$artifacts_dir" ]]; then
    echo "Refusing a symlink at test-environment/artifacts" >&2
    return 1
  fi
  if [[ ! -e "$artifacts_dir" ]]; then
    mkdir -- "$artifacts_dir"
  fi
  if [[ -L "$artifacts_dir" || ! -d "$artifacts_dir" ]]; then
    echo "Refusing a non-directory at test-environment/artifacts" >&2
    return 1
  fi

  (
    cd -- "$artifacts_dir"
    if [[ "$(pwd -P)" != "$artifacts_dir" ]]; then
      echo "Refusing an unexpected test-environment/artifacts target" >&2
      return 1
    fi
    if [[ -L health-probe || ( -e health-probe && ! -d health-probe ) ]]; then
      echo "Refusing an unexpected artifacts/health-probe target" >&2
      return 1
    fi
    if [[ -d health-probe ]]; then
      rm -rf -- health-probe
    fi
    mkdir -- health-probe
    [[ -d health-probe && ! -L health-probe ]]
  )
}

# Compose service name : paytech/ service directory (design/09 §3.1, one exec JAR per service).
readonly -a java_services=(
  risk-decision-service:risk-decision
  compliance-service:compliance
  platform-support-service:platform-support
  operations-console-bff:operations-console-bff
  api-gateway:api-gateway
  client-console-bff:client-console-bff
  client-master-service:client-master
  commercial-service:commercial
  acquiring-service:acquiring
  counterparty-service:counterparty
  payment-service:payment
  channel-connector-service:channel-connector
  card-service:card
  ledger-service:ledger
  billing-service:billing
  account-service:account
  reconciliation-service:reconciliation
  tax-service:tax
)

main() {
  environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
  local workspace_dir
  workspace_dir="$(dirname "$environment_dir")"
  export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home}"
  export PATH="$JAVA_HOME/bin:$PATH"
  local health_probe_output="$environment_dir/artifacts/health-probe"
  prepare_health_probe_output "$environment_dir"
  "$JAVA_HOME/bin/javac" --release 25 -encoding UTF-8 -d "$health_probe_output" "$environment_dir/java/LocalHttpHealthProbe.java" "$environment_dir/java/LocalSnapshotEvidenceFetch.java"
  # Build with the user's existing Maven Settings; do not copy credentials into Docker layers.
  # Single paytech/ Reactor (refactor steps 00-12): one Maven run packages every mor-test Java service; -am builds
  # build/, foundation/ and the api modules they need, so nothing is installed into the local repository.
  local service module
  local -a app_modules=()
  for service in "${java_services[@]}"; do
    module="${service#*:}"
    app_modules+=("$module/paytech-$module-app")
  done
  (cd "$workspace_dir/paytech" && ./mvnw -B -DskipTests -pl "$(IFS=,; echo "${app_modules[*]}")" -am clean package)
  (cd "$workspace_dir/operations-console-web" && npm ci && npm run build)
  # One JAR_FILE per Compose service: artifacts/<compose service>.jar.
  for service in "${java_services[@]}"; do
    module="${service#*:}"
    cp "$workspace_dir/paytech/$module/paytech-$module-app/target/paytech-$module-app-1.0.0-SNAPSHOT-exec.jar" \
      "$environment_dir/artifacts/${service%%:*}.jar"
  done
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi

[[ "${BASH_SOURCE[0]}" != "$0" ]] || "$environment_dir/compose.sh" build \
  nacos risk-decision-service compliance-service platform-support-service operations-console-bff api-gateway \
  client-console-bff client-master-service commercial-service acquiring-service counterparty-service payment-service \
  channel-connector-service card-service ledger-service billing-service account-service reconciliation-service \
  tax-service operations-console-web
