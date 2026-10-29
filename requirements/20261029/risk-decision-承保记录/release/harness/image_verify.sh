#!/usr/bin/env bash
# 候选镜像核对（只读，本机 docker run --network none）：Java 镜像内 JAR = 候选构建产物、health-probe = 现网、
# Entrypoint / User / Healthcheck / 端口 / WorkDir = 现网镜像；Web 镜像 dist = 发布 worktree 构建、nginx.conf = 现网。
# 改写自 REQ-05 release/harness/image_verify_java.sh。
set -euo pipefail
B=${B:-$HOME/Code-MoR-backups/rq03-20261029}; TAG=${TAG:-rq03-20261029a}; RL=${RL:-/Users/kenny/Desktop/Workspace/Code-MoR-rq03-rel}
f='{{json .Config.Entrypoint}}{{json .Config.Cmd}}{{json .Config.User}}{{json .Config.Healthcheck}}{{json .Config.ExposedPorts}}{{.Config.WorkingDir}}'
for svc in risk-decision-service operations-console-bff platform-support-service; do
  img=$(docker image inspect "mor-test/${svc}:${TAG}" --format '{{.Id}}')
  inimg=$(docker run --rm --network none --entrypoint sha256sum "$img" /app/application.jar | cut -c1-16)
  built=$(shasum -a 256 "$B/candidate/${svc}.jar" | cut -c1-16)
  probe=$(docker run --rm --network none --entrypoint sh "$img" -c 'cd /app/health-probe && sha256sum * | sha256sum' | cut -c1-16)
  probel=$(docker exec "mor-test-${svc}-1" sh -c 'cd /app/health-probe && sha256sum * | sha256sum' | cut -c1-16)
  live=$(docker inspect "mor-test-${svc}-1" --format '{{.Image}}')
  m1=$(docker image inspect "$img" --format "$f"); m2=$(docker image inspect "$live" --format "$f")
  echo "${svc} id=${img} jar_in_image=${inimg} built=${built} jar=$([ "$inimg" = "$built" ] && echo same || echo DIFF) probe=$([ "$probe" = "$probel" ] && echo same || echo DIFF) meta_vs_live=$([ "$m1" = "$m2" ] && echo same || echo DIFF)"
done
svc=operations-console-web; img=$(docker image inspect "mor-test/${svc}:${TAG}" --format '{{.Id}}'); live=$(docker inspect "mor-test-${svc}-1" --format '{{.Image}}')
d1=$(docker run --rm --network none --entrypoint sh "$img" -c 'cd /usr/share/nginx/operations-console && find . -type f | xargs sha256sum' | LC_ALL=C sort -k2 | shasum -a 256 | cut -c1-16)
d2=$(cd "$RL/operations-console-web/dist" && find . -type f | xargs shasum -a 256 | LC_ALL=C sort -k2 | shasum -a 256 | cut -c1-16)
n1=$(docker run --rm --network none --entrypoint sha256sum "$img" /etc/nginx/nginx.conf | cut -c1-16)
n2=$(docker exec "mor-test-${svc}-1" sha256sum /etc/nginx/nginx.conf | cut -c1-16)
m1=$(docker image inspect "$img" --format "$f"); m2=$(docker image inspect "$live" --format "$f")
echo "${svc} id=${img} dist=$([ "$d1" = "$d2" ] && echo same || echo DIFF) files=$(cd "$RL/operations-console-web/dist" && find . -type f | wc -l | tr -d ' ') nginx_conf=$([ "$n1" = "$n2" ] && echo same || echo DIFF) meta_vs_live=$([ "$m1" = "$m2" ] && echo same || echo DIFF)"
