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
  (cd "$workspace_dir/paytech-build" && ./mvnw -DskipTests clean install)
  (cd "$workspace_dir/paytech-foundation" && ./mvnw -pl paytech-mvc-web-starter,paytech-http-client-support,paytech-mybatis-support,paytech-redis-starter,paytech-snowflake-id,paytech-json-canonicalization -am -DskipTests clean install)
  (cd "$workspace_dir/risk-compliance-service" && ./mvnw -pl risk-web -am -DskipTests clean install)
  cp "$workspace_dir/risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar" \
    "$environment_dir/artifacts/risk-service.jar"
  (cd "$workspace_dir/platform-support-service" && ./mvnw -pl platform-support-web -am -DskipTests clean install)
  (cd "$workspace_dir/api-gateway" && ./mvnw -pl api-gateway-web -am -DskipTests clean package)
  (cd "$workspace_dir/operations-console-bff" && ./mvnw -pl paytech-operations-console-bff-web -am -DskipTests clean package)
  (cd "$workspace_dir/operations-console-web" && npm ci && npm run build)
  cp "$workspace_dir/platform-support-service/platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar" "$environment_dir/artifacts/platform-support-service.jar"
  cp "$workspace_dir/api-gateway/api-gateway-web/target/api-gateway-web-1.0.0-SNAPSHOT-exec.jar" "$environment_dir/artifacts/api-gateway.jar"
  cp "$workspace_dir/operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar" "$environment_dir/artifacts/operations-console-bff.jar"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi

[[ "${BASH_SOURCE[0]}" != "$0" ]] || "$environment_dir/compose.sh" build \
  nacos risk-service platform-support-service operations-console-bff api-gateway operations-console-web
