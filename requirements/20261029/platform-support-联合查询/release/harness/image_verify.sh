#!/usr/bin/env bash
# 联合查询 jq-20261030a 候选镜像核对（只读，本机 docker run --network none）：镜像内 JAR = 候选构建产物、health-probe = 现网、
# Entrypoint / User / Healthcheck / 端口 / WorkDir = 现网镜像。改写自 REQ-03 release/harness/image_verify.sh（只 Platform、ops-bff；ops-web 由 jq-web 另行核对）。
set -euo pipefail
B=${B:-$HOME/Desktop/Workspace/Code-MoR-backups/jq-20261030}; TAG=${TAG:-jq-20261030a}; CAND=${CAND:-$B/candidate}
f='{{json .Config.Entrypoint}}{{json .Config.Cmd}}{{json .Config.User}}{{json .Config.Healthcheck}}{{json .Config.ExposedPorts}}{{.Config.WorkingDir}}'
for svc in platform-support-service operations-console-bff; do
  img=$(docker image inspect "mor-test/${svc}:${TAG}" --format '{{.Id}}')
  inimg=$(docker run --rm --network none --entrypoint sha256sum "$img" /app/application.jar | cut -c1-16)
  built=$(shasum -a 256 "$CAND/${svc}.jar" | cut -c1-16)
  probe=$(docker run --rm --network none --entrypoint sh "$img" -c 'cd /app/health-probe && sha256sum * | sha256sum' | cut -c1-16)
  probel=$(docker exec "mor-test-${svc}-1" sh -c 'cd /app/health-probe && sha256sum * | sha256sum' | cut -c1-16)
  live=$(docker inspect "mor-test-${svc}-1" --format '{{.Image}}')
  m1=$(docker image inspect "$img" --format "$f"); m2=$(docker image inspect "$live" --format "$f")
  echo "${svc} id=${img} jar_in_image=${inimg} built=${built} jar=$([ "$inimg" = "$built" ] && echo same || echo DIFF) probe=$([ "$probe" = "$probel" ] && echo same || echo DIFF) meta_vs_live=$([ "$m1" = "$m2" ] && echo same || echo DIFF)"
done
