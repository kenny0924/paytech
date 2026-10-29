#!/usr/bin/env bash
# 候选 Java 镜像核对：镜像内 JAR = 构建产物、health-probe = 现网、Entrypoint/User/Healthcheck/端口/WorkDir = 现网镜像。只读（本机 docker run --network none）。
set -euo pipefail
B=/Users/kenny/Desktop/Workspace/Code-MoR-backups/wfdq-20261029/stage6-live; TAG=${TAG:-wfdq-20261029a}
for pair in "platform-support-service platform" "operations-console-bff opsbff"; do
  set -- $pair
  img=$(docker image inspect "mor-test/$1:$TAG" --format '{{.Id}}')
  inimg=$(docker run --rm --network none --entrypoint sha256sum "$img" /app/application.jar | cut -c1-16)
  built=$(shasum -a 256 "$B/candidate/$2.jar" | cut -c1-16)
  probe=$(docker run --rm --network none --entrypoint sh "$img" -c 'cd /app/health-probe && sha256sum * | sha256sum' | cut -c1-16)
  probel=$(docker exec "mor-test-$1-1" sh -c 'cd /app/health-probe && sha256sum * | sha256sum' | cut -c1-16)
  live=$(docker inspect "mor-test-$1-1" --format '{{.Image}}')
  f='{{json .Config.Entrypoint}}{{json .Config.User}}{{json .Config.Healthcheck}}{{json .Config.ExposedPorts}}{{.Config.WorkingDir}}'
  m1=$(docker image inspect "$img" --format "$f"); m2=$(docker image inspect "$live" --format "$f")
  echo "$1 id=$img jar_in_image=$inimg built=$built probe=$([ "$probe" = "$probel" ] && echo same || echo DIFF) meta_vs_live=$([ "$m1" = "$m2" ] && echo same || echo DIFF)"
done
