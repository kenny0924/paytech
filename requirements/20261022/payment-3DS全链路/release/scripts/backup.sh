#!/usr/bin/env bash
# 3DS 全链路 + 卡 BIN 库合并发布 · mor-test 执行前备份（阶段 7 第 S2 步；只读 mor-test，写本机备份目录）。阶段 6 编写，未执行。
# 备份：mor_card、mor_payment、mor_channel_connector、mor_risk_decision、mor_platform 五库整库（结构 + 数据，--single-transaction）；
#       十个容器的运行镜像 ID / 启动时间 / 重启次数、九个 Java 服务运行 JAR 的 SHA-256（容器内 sha256sum，不复制 JAR）、环境变量键名（不含值）；
#       候选 / 回滚覆盖文件与 00-precheck 输出。目录 0700、文件 0600，末尾写 SHA256SUMS。口令只经 MYSQL_PWD 传入容器，不回显。
# 用法：requirements/20261022/payment-3DS全链路/release/scripts/backup.sh  → 打印备份目录
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/Code-MoR-backups/${stamp}-card3ds-mor-test"
umask 077
mkdir -p "$dir"
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
for db in mor_card mor_payment mor_channel_connector mor_risk_decision mor_platform; do
  docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
    --no-tablespaces --default-character-set=utf8mb4 --routines=false --triggers "$db" > "$dir/$db-full.sql"
  [[ -s "$dir/$db-full.sql" ]] || { echo "empty dump: $db" >&2; exit 1; }
done
services=(risk-decision-service payment-service acquiring-service counterparty-service platform-support-service card-service
          channel-connector-service api-gateway operations-console-bff operations-console-web)
for s in "${services[@]}"; do
  printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} {{.State.Health.Status}} restarts={{.RestartCount}}')"
  docker exec "mor-test-$s-1" printenv | cut -d= -f1 | sort > "$dir/env-keys-$s-before.txt"
done > "$dir/images-before.txt"
for s in "${services[@]:0:9}"; do
  printf '%s %s\n' "$s" "$(docker exec "mor-test-$s-1" sha256sum /app/application.jar | cut -d' ' -f1)"
done > "$dir/jars-before.txt"
cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t \
  < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
chmod 600 "$dir"/*
(cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
echo "$dir"
