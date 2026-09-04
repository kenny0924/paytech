#!/usr/bin/env bash
set -euo pipefail
environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workspace_dir="$(dirname "$environment_dir")"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$environment_dir/artifacts"
# Build with the user's existing Maven Settings; do not copy credentials into Docker layers.
(cd "$workspace_dir/paytech-build" && ./mvnw -DskipTests install)
(cd "$workspace_dir/paytech-foundation" && ./mvnw -pl paytech-mvc-web-starter,paytech-http-client-support,paytech-mybatis-support,paytech-snowflake-id -am -DskipTests install)
(cd "$workspace_dir/platform-support-service" && ./mvnw -pl platform-support-web -am -DskipTests install)
(cd "$workspace_dir/api-gateway" && ./mvnw -pl api-gateway-web -am -DskipTests package)
(cd "$workspace_dir/operations-console-bff" && ./mvnw -pl paytech-operations-console-bff-web -am -DskipTests package)
(cd "$workspace_dir/operations-console-web" && npm ci && npm run build)
cp "$workspace_dir/platform-support-service/platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar" "$environment_dir/artifacts/platform-support-service.jar"
cp "$workspace_dir/api-gateway/api-gateway-web/target/api-gateway-web-1.0.0-SNAPSHOT-exec.jar" "$environment_dir/artifacts/api-gateway.jar"
cp "$workspace_dir/operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar" "$environment_dir/artifacts/operations-console-bff.jar"
"$environment_dir/compose.sh" build
