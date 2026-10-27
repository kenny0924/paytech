#!/usr/bin/env bash
# 断网一次性 MySQL 演练：导入一个库的只读备份，按顺序执行 SQL 步骤，输出每步结果。不连任何环境。
# 用法：db_dryrun.sh <库名> <备份 .sql（mysqldump --databases 格式）> <工作目录> <标签>=<SQL 文件>...
# 环境变量：MYSQL_IMAGE（默认 mysql:8.4.11，应与 mor-test 同一镜像 ID）、KEEP_CONTAINER=1。
# 任一步 SQL 失败即停止（退出码 1）；输出 <工作目录>/dryrun.log。
set -euo pipefail
db="${1:?db}"; dump="${2:?dump}"; work="${3:?work}"; shift 3
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; c="dryrun-$db-$$"; mkdir -p "$work"; log="$work/dryrun.log"; : > "$log"
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=throwaway "$IMAGE" --character-set-server=utf8mb4 >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done  # 等最终实例（初始化阶段的临时实例不监听 3306）
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=throwaway mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
docker exec -i "$c" sh -c "MYSQL_PWD=throwaway mysql -uroot --default-character-set=utf8mb4" < "$dump"
echo "### imported $dump" | tee -a "$log"
for stepdef in "$@"; do
  label="${stepdef%%=*}"; file="${stepdef#*=}"; echo "### $label: $file" | tee -a "$log"
  rc=0; docker exec -i "$c" sh -c "MYSQL_PWD=throwaway mysql -uroot --default-character-set=utf8mb4 -t $db" < "$file" > "$work/$label.out" 2>&1 || rc=$?
  grep -v "Using a password" "$work/$label.out" | tee -a "$log" || true
  [[ "$rc" == 0 ]] || { echo "### FAILED: $label (exit $rc)" | tee -a "$log"; exit 1; }
done
echo "### ALL STEPS OK" | tee -a "$log"
