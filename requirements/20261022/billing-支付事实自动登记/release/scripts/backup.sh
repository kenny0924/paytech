#!/usr/bin/env bash
# billing 支付事实自动登记 · mor-test 执行前备份（阶段 7 第 2 步；只读 mor-test，写本机备份目录）。
# 备份：五个服务（billing、account、ledger、acquiring、payment）的运行镜像 ID 与运行 JAR SHA-256；候选 / 回滚覆盖文件；
#   00-precheck 输出；RabbitMQ 相关队列状态；验收会写入的表（mysqldump --single-transaction，只读）：
#   mor_billing 事实四表 + 日历 + 补偿 / 消费登记；mor_account 过账 / 分录 / Owner 账户 / 已应用事实 / 余额移动 / 序列 / 补偿 / 消费登记；
#   mor_ledger 指令 / 分录 / 账户 / 期间余额 / 序列 / 补偿 / 消费登记；mor_reconciliation 平台流水 / 补偿 / 消费登记；
#   mor_channel_connector 渠道成本四表与变更记录（Q-2 写入前）。mor_billing 含 t_business_calendar（SQL-1 写入前）。
# 本需求无 DDL；SQL-1（日历一行）、渠道成本方案（运营写入）与合成 OrderPaymentSucceeded 验收在本备份之后写入上述表。目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：requirements/20261022/billing-支付事实自动登记/release/scripts/backup.sh  → 打印备份目录
# 口令只经 MYSQL_PWD 传入容器，RabbitMQ 凭据只在 queue_status.py 进程内使用，均不回显。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
env_file="$repo/test-environment/.env.test"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-billing-b-mor-test"
umask 077
mkdir -p "$dir"
chmod 700 "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$env_file" | cut -d= -f2-)"

dump() {
  local db="$1"; shift
  docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
    --no-tablespaces --default-character-set=utf8mb4 --skip-triggers "$db" "$@" > "$dir/$db.sql"
  [[ -s "$dir/$db.sql" ]] || { echo "empty dump: $db" >&2; exit 1; }
}
dump mor_billing t_billing_fact t_billing_fact_line t_billing_fact_ref t_billing_fact_pricing t_business_calendar \
  t_business_calendar_day t_compensation_event t_consumed_event t_consumed_aggregate_version
dump mor_account t_account_posting t_account_entry t_owner_account t_owner_account_no_sequence t_balance_applied_fact \
  t_balance_movement t_account_posting_stream t_compensation_event t_consumed_event t_consumed_aggregate_version
dump mor_ledger t_accounting_instruction t_instruction_line t_journal_entry t_journal_line t_ledger_account \
  t_ledger_period_balance t_book_sequence t_account_no_sequence t_compensation_event t_consumed_event t_consumed_aggregate_version
dump mor_reconciliation t_platform_txn t_compensation_event t_consumed_event t_consumed_aggregate_version
dump mor_channel_connector t_channel_cost_plan t_channel_cost_plan_ver t_channel_cost_scope t_channel_cost_component \
  t_channel_config_change

services=(billing-service account-service ledger-service acquiring-service payment-service)
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} {{.State.Health.Status}} restarts={{.RestartCount}}')"
done > "$dir/images-before.txt"
for s in "${services[@]}"; do
  docker cp "mor-test-$s-1:/app/application.jar" "$dir/$s-before.jar" >/dev/null
  printf '%s %s\n' "$s" "$(shasum -a 256 "$dir/$s-before.jar" | cut -d' ' -f1)" >> "$dir/jars-before.txt"
  rm -f "$dir/$s-before.jar"
done
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
python3 -I "$here/acceptance/queue_status.py" > "$dir/queues-before.txt"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
echo "$dir"
