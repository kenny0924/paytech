#!/usr/bin/env bash
# acquiring 到期后支付结果确认 · mor-test 只读快照（阶段 6 编写并执行；阶段 7 S1 / 替换后复跑，输出目录作参数）。
# 只读：docker inspect / images、容器内 printenv（只取键名）与 sha256sum、xxl_job / mor_acquiring / mor_payment 只读 SELECT、
#   rabbitmqctl list_queues、compose.sh config 渲染后的逐键比较（scripts/env_compare.py，不输出值）。不写任何库、容器、XXL、RabbitMQ。
# 用法：scripts/snapshot.sh <输出目录>
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release="$(cd "$here/.." && pwd)"
repo="$(cd "$release/../../../.." && pwd)"
out="${1:?输出目录}"
umask 077
mkdir -p "$out"
{
  date -u +%Y-%m-%dT%H:%M:%SZ
  for s in acquiring-service payment-service billing-service xxl-job-admin; do
    docker inspect "mor-test-$s-1" --format "$s {{.Image}} started={{.State.StartedAt}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} restarts={{.RestartCount}} config={{index .Config.Labels \"com.docker.compose.project.config_files\"}}"
  done
} > "$out/containers.txt"
for s in acquiring-service payment-service; do
  printf '%s %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /app/application.jar | cut -d' ' -f1)"
done > "$out/jar-sha.txt"
docker exec mor-test-acquiring-service-1 printenv | cut -d= -f1 | sort > "$out/env-keys-acquiring-service.txt"
docker images --no-trunc --format '{{.ID}} {{.Repository}}:{{.Tag}} {{.CreatedAt}}' | grep -E 'acquiring-service' > "$out/images.txt" || true
python3 -I "$here/env_compare.py" acquiring-service > "$out/env-compare-main-compose.txt" || true
python3 -I "$here/env_compare.py" --override "$repo/requirements/20261022/billing-支付事实自动登记/release/compose-candidate.yaml" \
  acquiring-service > "$out/env-compare-billing-candidate.txt" || true
for f in compose-candidate compose-rollback; do
  python3 -I "$here/env_compare.py" --override "$release/$f.yaml" acquiring-service > "$out/env-compare-$f.txt" || true
done
started="$(docker inspect mor-test-acquiring-service-1 --format '{{.State.StartedAt}}')"
printf 'since %s ERROR lines: %s\n' "$started" "$(docker logs --since "$started" mor-test-acquiring-service-1 2>&1 | grep -c ' ERROR ' || true)" > "$out/acquiring-error-count.txt"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -e "
SELECT id, app_name, address_type, address_list, update_time FROM xxl_job.xxl_job_group WHERE app_name='acquiring-service';
SELECT registry_key, registry_value, update_time, TIMESTAMPDIFF(SECOND, update_time, NOW()) AS age_s FROM xxl_job.xxl_job_registry WHERE registry_key='acquiring-service';
SELECT id, job_group, executor_handler, schedule_conf, executor_route_strategy, executor_block_strategy, executor_timeout, trigger_status FROM xxl_job.xxl_job_info WHERE job_group=4 ORDER BY id;
SELECT job_id, COUNT(*) AS runs_30m, SUM(trigger_code=200) AS trigger_ok, SUM(handle_code=200) AS handle_ok, SUM(handle_code NOT IN (0,200)) AS handle_fail, MAX(trigger_time) AS last_trigger FROM xxl_job.xxl_job_log WHERE job_group=4 AND trigger_time >= NOW() - INTERVAL 30 MINUTE GROUP BY job_id;
" > "$out/xxl.txt"
docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -e "
SELECT UTC_TIMESTAMP(6) AS now_utc;
-- 到期订单分布（按状态 / 是否有支付记录 / 投影状态）
SELECT o.order_status, (p.order_id IS NOT NULL) AS has_projection, IFNULL(p.intent_status,'-') AS intent_status, IFNULL(p.last_attempt_status,'-') AS last_attempt_status,
  COUNT(*) AS n, MIN(o.expires_at) AS min_expires_at, MAX(o.expires_at) AS max_expires_at
FROM mor_acquiring.t_commerce_order o LEFT JOIN mor_acquiring.t_order_payment_projection p ON p.order_id=o.order_id
WHERE o.expires_at < UTC_TIMESTAMP(6) GROUP BY 1,2,3,4 ORDER BY 1,2;
SELECT order_status, COUNT(*) AS n_not_expired FROM mor_acquiring.t_commerce_order WHERE expires_at >= UTC_TIMESTAMP(6) GROUP BY 1;
-- 有支付记录的订单在 Payment 中是否有真实 Intent / Attempt（夹具投影没有）
SELECT COUNT(*) AS projections,
  SUM(EXISTS(SELECT 1 FROM mor_payment.t_payment_intent i WHERE i.payment_intent_id=p.payment_intent_id)) AS with_payment_intent,
  SUM(EXISTS(SELECT 1 FROM mor_payment.t_payment_attempt a WHERE a.payment_intent_id=p.payment_intent_id)) AS with_payment_attempt
FROM mor_acquiring.t_order_payment_projection p;
SELECT intent_status, COUNT(*) AS n FROM mor_payment.t_payment_intent GROUP BY 1;
SELECT attempt_status, COUNT(*) AS n FROM mor_payment.t_payment_attempt GROUP BY 1;
-- acquiring 补偿任务基线
SELECT event_type, IFNULL(sub_event_type,'') AS sub_event_type, execute_status, COUNT(*) AS n FROM mor_acquiring.t_compensation_event GROUP BY 1,2,3 ORDER BY 1,2,3;
-- 未完成的到期终止请求（回滚前须为 0 或列出）
SELECT COUNT(*) AS open_with_termination_requested FROM mor_acquiring.t_commerce_order WHERE termination_requested_at IS NOT NULL AND order_status='OPEN';
" > "$out/db-precheck.txt"
docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues -p /mor-test name messages consumers 2>/dev/null \
  | grep -E '^acquiring\.|^billing\.acquiring' > "$out/rabbitmq-queues.txt" || true
chmod 600 "$out"/*
echo "$out"
