#!/usr/bin/env bash
# 文件公开桶 · mor-test 执行前备份（阶段 7 第 2 步；只读 mor-test，写本机备份目录）。
# 备份：mor_platform 中 DDL 涉及的 5 张文件表、存储实例 / 扫描策略 / 检查要求，以及 SQL 涉及的 Gateway / 控制台 / 权限 7 张表；
#       六个容器的运行镜像 ID / 启动时间 / 健康 / 重启次数、五个 Java 服务运行 JAR 的 SHA-256、六个服务环境变量键名（不含值）；
#       候选 / 回滚覆盖文件与 00-precheck 输出。目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：requirements/20261022/platform-support-文件公开桶/release/scripts/backup.sh  → 打印备份目录
# 口令只经 MYSQL_PWD 传入容器，不回显。容器一律按完整名称指定。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
env_file="$repo/test-environment/.env.test"
mysql_container=mor-test-mysql-1
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-pubbucket-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$env_file" | cut -d= -f2-)"
dump() {  # dump <file> <db> [tables...]
  local out="$1"; shift
  docker exec -e MYSQL_PWD="$root_pw" "$mysql_container" mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
    --no-tablespaces --default-character-set=utf8mb4 --routines=false --triggers "$@" > "$dir/$out"
}
dump mor_platform_files.sql mor_platform t_file_object t_file_upload t_file_scan_result t_file_storage_bucket \
  t_file_storage_route t_file_storage_instance t_file_scan_policy t_file_scan_requirement
dump mor_platform_registration.sql mor_platform t_permission t_role t_role_permission t_console_resource t_gateway_route \
  t_gateway_route_header t_gateway_route_query_param t_gateway_guard_rule
services=(card-service client-master-service client-console-bff operations-console-bff platform-support-service operations-console-web)
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} {{.State.Health.Status}} restarts={{.RestartCount}}')"
done > "$dir/images-before.txt"
for s in card-service client-master-service client-console-bff operations-console-bff platform-support-service; do
  docker cp "mor-test-$s-1:/app/application.jar" "$dir/$s-before.jar" >/dev/null
  printf '%s %s\n' "$s" "$(shasum -a 256 "$dir/$s-before.jar" | cut -d' ' -f1)" >> "$dir/jars-before.txt"
  rm -f "$dir/$s-before.jar"
done
for s in "${services[@]}"; do
  docker exec "mor-test-$s-1" printenv | cut -d= -f1 | sort | sed "s/^/$s /"
done > "$dir/env-keys-before.txt"
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" "$mysql_container" mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
for f in mor_platform_files.sql mor_platform_registration.sql; do
  [[ -s "$dir/$f" ]] || { echo "empty dump: $f" >&2; exit 1; }
done
echo "$dir"
