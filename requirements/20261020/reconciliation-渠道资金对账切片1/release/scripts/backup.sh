#!/usr/bin/env bash
# 对账切片 1 · mor-test 执行前备份（阶段 7 第 1 步；只读 mor-test，写本机备份目录）。
# 备份：mor_reconciliation 整库、mor_billing 整库、mor_platform 注册 8 表、mor_vault 策略 3 表；五个服务的运行镜像 ID 与运行 JAR SHA-256；
#       候选 / 回滚 compose 覆盖文件与 00-precheck 输出。目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：requirements/20261020/reconciliation-渠道资金对账切片1/release/scripts/backup.sh  → 打印备份目录
# 口令只经 MYSQL_PWD 传入容器，不回显。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
env_file="$repo/test-environment/.env.test"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-recon-slice1-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$env_file" | cut -d= -f2-)"
dump() {  # dump <file> <db> [tables...]
  local out="$1"; shift
  docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
    --no-tablespaces --default-character-set=utf8mb4 --routines=false --triggers "$@" > "$dir/$out"
}
dump mor_reconciliation.sql mor_reconciliation
dump mor_billing.sql mor_billing
dump mor_platform_registration.sql mor_platform t_permission t_role t_role_permission t_console_resource t_gateway_route \
  t_gateway_route_header t_gateway_route_query_param t_gateway_guard_rule
dump mor_vault_policy.sql mor_vault t_vault_data_type t_vault_access_rule t_vault_policy_change
{
  for s in reconciliation-service billing-service operations-console-bff operations-console-web platform-support-service; do
    printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} restarts={{.RestartCount}}')"
  done
} > "$dir/images-before.txt"
for s in reconciliation-service billing-service operations-console-bff platform-support-service; do
  docker cp "mor-test-$s-1:/app/application.jar" "$dir/$s-before.jar" >/dev/null
  printf '%s %s\n' "$s" "$(shasum -a 256 "$dir/$s-before.jar" | cut -d' ' -f1)" >> "$dir/jars-before.txt"
  rm -f "$dir/$s-before.jar"
done
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
for f in mor_reconciliation.sql mor_billing.sql mor_platform_registration.sql mor_vault_policy.sql; do
  [[ -s "$dir/$f" ]] || { echo "empty dump: $f" >&2; exit 1; }
done
echo "$dir"
