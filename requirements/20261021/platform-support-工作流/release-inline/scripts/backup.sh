#!/usr/bin/env bash
# 工作流附件内嵌预览跟进 · mor-test 执行前备份（阶段 7 第 2 步；只读 mor-test，写本机备份目录）。
# 备份：mor_platform 整库（--single-transaction）；三个容器的运行镜像 ID / 启动时间 / 健康 / 重启次数；两个 Java 服务运行 JAR SHA-256
#       （容器内 sha256sum，只读）；platform-support-service 与 operations-console-bff 环境变量键名（不含值）；候选 / 回滚覆盖文件与 00-precheck 输出。
#       目录 0700、文件 0600，末尾写 SHA256SUMS。
# 用法：requirements/20261021/platform-support-工作流/release-inline/scripts/backup.sh  → 打印备份目录
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-workflow-inline-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
  --no-tablespaces --default-character-set=utf8mb4 --routines --triggers --databases mor_platform > "$dir/mor_platform-full.sql"
for s in platform-support-service operations-console-bff operations-console-web; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} {{.State.Health.Status}} restarts={{.RestartCount}}')"
done > "$dir/images-before.txt"
for s in platform-support-service operations-console-bff; do
  printf '%s %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /app/application.jar | cut -d' ' -f1)"
done > "$dir/jars-before.txt"
for s in platform-support-service operations-console-bff; do
  docker exec "mor-test-$s-1" printenv | cut -d= -f1 | sort > "$dir/$s-env-keys-before.txt"
done
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
[[ -s "$dir/mor_platform-full.sql" ]] || { echo "empty dump" >&2; exit 1; }
echo "$dir"
