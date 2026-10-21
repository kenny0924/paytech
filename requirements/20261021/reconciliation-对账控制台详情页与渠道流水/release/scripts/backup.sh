#!/usr/bin/env bash
# 对账控制台详情页与渠道流水 · mor-test 执行前备份（阶段 7 第 2 步；只读 mor-test，写本机备份目录）。
# 备份：mor_platform 注册 8 表（块 126 写入前）；四个服务的运行镜像 ID 与运行 JAR SHA-256；候选 / 回滚覆盖文件；00-precheck 输出。
# 本需求不改 mor_reconciliation 结构与数据，不备份该库。目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：requirements/20261021/reconciliation-对账控制台详情页与渠道流水/release/scripts/backup.sh  → 打印备份目录
# 口令只经 MYSQL_PWD 传入容器，不回显。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
env_file="$repo/test-environment/.env.test"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-recon-detail-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$env_file" | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
  --no-tablespaces --default-character-set=utf8mb4 --routines=false --triggers mor_platform t_permission t_role t_role_permission \
  t_console_resource t_gateway_route t_gateway_route_header t_gateway_route_query_param t_gateway_guard_rule \
  > "$dir/mor_platform_registration.sql"
services=(reconciliation-service platform-support-service operations-console-bff operations-console-web)
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} restarts={{.RestartCount}}')"
done > "$dir/images-before.txt"
for s in reconciliation-service platform-support-service operations-console-bff; do
  docker cp "mor-test-$s-1:/app/application.jar" "$dir/$s-before.jar" >/dev/null
  printf '%s %s\n' "$s" "$(shasum -a 256 "$dir/$s-before.jar" | cut -d' ' -f1)" >> "$dir/jars-before.txt"
  rm -f "$dir/$s-before.jar"
done
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
[[ -s "$dir/mor_platform_registration.sql" ]] || { echo "empty dump: mor_platform_registration.sql" >&2; exit 1; }
echo "$dir"
