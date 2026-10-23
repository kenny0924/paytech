#!/usr/bin/env bash
# 多语言管理 · mor-test 执行前备份（阶段 7 第 2 步；只读 mor-test，写本机备份目录）。
# 内容：mor_platform 全库（mysqldump --single-transaction）；5 个容器的运行镜像 ID / 启动时间 / 健康 / 重启次数；
#       4 个服务（3 个 Java 的 /app/application.jar 与 2 个 Web 的 nginx.conf）在容器内的 SHA-256（docker exec sha256sum，不 docker cp）；
#       5 个服务环境变量键名（不含值）；候选 / 回滚覆盖文件；00-precheck 输出。目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：B=$(requirements/20261022/platform-support-多语言管理/release/scripts/backup.sh)   → 打印备份目录
# 口令只经 MYSQL_PWD 传入容器，不回显。容器一律按完整名称指定。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
mysql_container=mor-test-mysql-1
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-i18n-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$root_pw" "$mysql_container" mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
  --no-tablespaces --default-character-set=utf8mb4 --routines=false --triggers --databases mor_platform > "$dir/mor_platform-full.sql"
services=(platform-support-service operations-console-bff client-console-bff operations-console-web client-console-web)
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} {{.State.Health.Status}} restarts={{.RestartCount}}')"
done > "$dir/images-before.txt"
for s in platform-support-service operations-console-bff client-console-bff; do
  printf '%s %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /app/application.jar | cut -d' ' -f1)"
done > "$dir/jars-before.txt"
for s in operations-console-web client-console-web; do
  printf '%s nginx.conf %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /etc/nginx/nginx.conf | cut -d' ' -f1)"
done >> "$dir/jars-before.txt"
for s in "${services[@]}"; do
  docker exec "mor-test-$s-1" printenv | cut -d= -f1 | sort > "$dir/$s-env-keys-before.txt"
done
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" "$mysql_container" mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
[[ -s "$dir/mor_platform-full.sql" ]] && grep -q 'CREATE TABLE `t_file_storage_route`' "$dir/mor_platform-full.sql" \
  || { echo "incomplete dump: $dir/mor_platform-full.sql" >&2; exit 1; }
echo "$dir"
