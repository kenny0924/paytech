#!/usr/bin/env bash
# acquiring 订单过期收尾 · mor-test 只读快照（阶段 6 编写并执行；阶段 7 S1 / S2 复跑，输出目录作参数）。
# 只读：docker inspect / ps、容器内 printenv（只取键名）与 sha256sum、xxl_job 与 RabbitMQ 只读查询、00-precheck.sql、
#   compose.sh config 渲染后的键名比较（scripts/env_compare.py，不输出值）。不写任何库、容器、XXL、RabbitMQ。
# 用法：scripts/snapshot.sh <输出目录>
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release="$(cd "$here/.." && pwd)"
repo="$(cd "$release/../../../.." && pwd)"
out="${1:?输出目录}"
umask 077
mkdir -p "$out"
services=(acquiring-service payment-service platform-support-service)
{
  date -u +%Y-%m-%dT%H:%M:%SZ
  for s in "${services[@]}" xxl-job-admin webhook-receiver; do
    docker inspect "mor-test-$s-1" --format "$s {{.Image}} started={{.State.StartedAt}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} restarts={{.RestartCount}} config={{index .Config.Labels \"com.docker.compose.project.config_files\"}}"
  done
} > "$out/containers.txt"
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /app/application.jar | cut -d' ' -f1)"
done > "$out/jar-sha.txt"
for s in acquiring-service payment-service; do
  docker exec "mor-test-$s-1" printenv | cut -d= -f1 | sort > "$out/env-keys-$s.txt"
done
docker images --no-trunc --format '{{.ID}} {{.Repository}}:{{.Tag}} {{.CreatedAt}}' \
  | grep -E 'acquiring-service|payment-service' > "$out/images.txt" || true
python3 -I "$here/env_compare.py" acquiring-service payment-service > "$out/env-compare-main-compose.txt" || true
python3 -I "$here/env_compare.py" --override "$repo/requirements/20261022/payment-3DS全链路/release/compose-candidate.yaml" \
  acquiring-service payment-service > "$out/env-compare-3ds-candidate.txt" || true
for f in compose-candidate compose-rollback; do
  python3 -I "$here/env_compare.py" --override "$release/$f.yaml" acquiring-service payment-service > "$out/env-compare-$f.txt" || true
done
docker exec mor-test-platform-support-service-1 printenv SPRING_APPLICATION_JSON | python3 -I -c \
  'import sys,json; l=json.load(sys.stdin)["webhook"]["http"]["callers"].get("acquiring-service",[]); print("platform caller acquiring-service contains order.expired:", "order.expired" in l, "events:", len(l))' \
  > "$out/platform-webhook-caller.txt"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -e "
SELECT id, app_name, address_type, address_list, update_time FROM xxl_job.xxl_job_group WHERE app_name IN ('acquiring-service','payment-service');
SELECT registry_key, registry_value, update_time, TIMESTAMPDIFF(SECOND, update_time, NOW()) AS age_s FROM xxl_job.xxl_job_registry WHERE registry_key IN ('acquiring-service','payment-service');
SELECT id, job_group, executor_handler, schedule_conf, executor_route_strategy, executor_block_strategy, executor_timeout, trigger_status FROM xxl_job.xxl_job_info WHERE job_group IN (4,9) OR executor_handler='orderExpiryScan' ORDER BY id;
SELECT MAX(id) AS max_job_id, COUNT(*) AS jobs FROM xxl_job.xxl_job_info;
SELECT job_id, COUNT(*) AS runs_30m, SUM(trigger_code=200) AS trigger_ok, SUM(handle_code=200) AS handle_ok, SUM(handle_code NOT IN (0,200)) AS handle_fail, MAX(trigger_time) AS last_trigger FROM xxl_job.xxl_job_log WHERE job_id IN (4,9) AND trigger_time >= NOW() - INTERVAL 30 MINUTE GROUP BY job_id;
" > "$out/xxl.txt"
docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues -p /mor-test name messages consumers 2>/dev/null \
  | grep -E '^(acquiring|card)\.payment|^payment\.' > "$out/rabbitmq-queues.txt" || true
"$here/precheck.sh" > "$out/precheck.out"
chmod 600 "$out"/*
echo "$out"
