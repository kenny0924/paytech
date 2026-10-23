#!/usr/bin/env bash
# 工作流体验优化 · mor-test 执行前备份（release.md 第 5 节第 2 步；只读 mor-test，写本机备份目录）。
# 备份：mor_platform 整库（--single-transaction）；六个容器（本包覆盖文件涉及的服务）的运行镜像 ID / 启动时间 / 健康 / 重启次数；
#       五个 Java 服务运行 JAR 的 SHA-256（容器内 sha256sum，只读）；六个服务环境变量键名（不含值）；候选 / 回滚覆盖文件与 00-precheck 输出。
#       目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：requirements/20261022/platform-support-工作流体验优化/release/scripts/backup.sh  → 打印备份目录
# 口令只经 MYSQL_PWD 传入容器，不回显。容器一律按完整名称指定。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
env_file="$repo/test-environment/.env.test"
mysql_container=mor-test-mysql-1
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-workflow-ux-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$env_file" | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$root_pw" "$mysql_container" mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
  --no-tablespaces --default-character-set=utf8mb4 --routines --triggers --databases mor_platform > "$dir/mor_platform-full.sql"
services=(platform-support-service operations-console-bff operations-console-web card-service client-master-service client-console-bff)
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} {{.State.Health.Status}} restarts={{.RestartCount}}')"
done > "$dir/images-before.txt"
for s in platform-support-service operations-console-bff card-service client-master-service client-console-bff; do
  printf '%s %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /app/application.jar | cut -d' ' -f1)"
done > "$dir/jars-before.txt"
for s in "${services[@]}"; do
  docker exec "mor-test-$s-1" printenv | cut -d= -f1 | sort > "$dir/$s-env-keys-before.txt"
done
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" "$mysql_container" mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
[[ -s "$dir/mor_platform-full.sql" ]] || { echo "empty dump" >&2; exit 1; }
grep -q 'CREATE TABLE `t_workflow_action_run`' "$dir/mor_platform-full.sql" || { echo "dump lacks t_workflow_action_run" >&2; exit 1; }
echo "$dir"
