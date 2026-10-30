#!/usr/bin/env bash
# 候选镜像核对（只读，本机 docker run --network none；对现网只 docker exec sha256sum / inspect）：
# Java 镜像内 JAR = 候选构建产物、health-probe = 现网同服务容器、Entrypoint / Cmd / User / Healthcheck / 端口 / WorkDir / Env 元数据 = 现网同服务镜像；
# Web 镜像 dist = 候选树构建、nginx.conf = 现网、元数据 = 现网。改写自 payout release/harness/image_verify.sh。
set -euo pipefail
B=${B:-$HOME/Code-MoR-backups/req02-stage6}; RL=${RL:-/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel}
# Q-4 修订后只重建 commercial / ops-web（tag b、产物 candidate-b/）；channel-connector / client-master 沿用 a。
tag_of() { case $1 in commercial-service|operations-console-web) echo req02-20261030b;; *) echo req02-20261030a;; esac; }
dir_of() { case $1 in commercial-service|operations-console-web) echo candidate-b;; *) echo candidate;; esac; }
f='{{json .Config.Entrypoint}}{{json .Config.Cmd}}{{json .Config.User}}{{json .Config.Healthcheck}}{{json .Config.ExposedPorts}}{{.Config.WorkingDir}}{{json .Config.Env}}'
probe_sum() { LC_ALL=C sort -k2 | awk '{print $1}' | tr '\n' ' '; }
for svc in channel-connector-service client-master-service commercial-service; do
  img=$(docker image inspect "mor-test/${svc}:$(tag_of $svc)" --format '{{.Id}}')
  inimg=$(docker run --rm --network none --entrypoint sha256sum "$img" /app/application.jar | cut -c1-16)
  built=$(shasum -a 256 "$B/$(dir_of $svc)/${svc}.jar" | cut -c1-16)
  probe=$(docker run --rm --network none --entrypoint sh "$img" -c 'cd /app/health-probe && sha256sum *' | probe_sum)
  probel=$(docker exec "mor-test-${svc}-1" sh -c 'cd /app/health-probe && sha256sum *' | probe_sum)
  live=$(docker inspect "mor-test-${svc}-1" --format '{{.Image}}')
  m1=$(docker image inspect "$img" --format "$f"); m2=$(docker image inspect "$live" --format "$f")
  echo "${svc} id=${img} jar_in_image=${inimg} built=${built} jar=$([ "$inimg" = "$built" ] && echo same || echo DIFF) probe_vs_live=$([ "$probe" = "$probel" ] && echo same || echo DIFF) meta_vs_live=$([ "$m1" = "$m2" ] && echo same || echo DIFF)"
done
svc=operations-console-web; img=$(docker image inspect "mor-test/${svc}:$(tag_of $svc)" --format '{{.Id}}'); live=$(docker inspect "mor-test-${svc}-1" --format '{{.Image}}')
d1=$(docker run --rm --network none --entrypoint sh "$img" -c 'cd /usr/share/nginx/operations-console && find . -type f | xargs sha256sum' | LC_ALL=C sort -k2 | awk '{print $1, $2}' | shasum -a 256 | cut -c1-16)
d2=$(cd "$B/candidate-b/ops-web-dist" && find . -type f | xargs shasum -a 256 | LC_ALL=C sort -k2 | awk '{print $1, $2}' | shasum -a 256 | cut -c1-16)
n1=$(docker run --rm --network none --entrypoint sha256sum "$img" /etc/nginx/nginx.conf | cut -c1-16)
n2=$(docker exec "mor-test-${svc}-1" sha256sum /etc/nginx/nginx.conf | cut -c1-16)
m1=$(docker image inspect "$img" --format "$f"); m2=$(docker image inspect "$live" --format "$f")
echo "${svc} id=${img} dist=$([ "$d1" = "$d2" ] && echo same || echo DIFF) files=$(cd "$B/candidate-b/ops-web-dist" && find . -type f | wc -l | tr -d ' ') nginx_conf=$([ "$n1" = "$n2" ] && echo same || echo DIFF) meta_vs_live=$([ "$m1" = "$m2" ] && echo same || echo DIFF)"
