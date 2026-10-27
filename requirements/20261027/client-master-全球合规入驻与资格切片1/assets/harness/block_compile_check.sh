#!/usr/bin/env bash
# Platform 注册块发布前门禁：在断网的一次性 MySQL 容器里导入 mor_platform 备份，按顺序执行块 SQL，每一步之后用候选 Platform JAR
# 内的生产 GatewayConfigurationCompiler 编译 Gateway 快照（DISABLED 路由同样参与 guard / path / header 校验，只有路径冲突限 ACTIVE）。
#
# 用法：block_compile_check.sh <mor_platform 备份 .sql> <Platform JAR（*-exec.jar）或已解出目录> <工作目录> <步骤>...
#   步骤：<标签>=<SQL 文件>，按给出顺序执行；首个编译固定为 baseline（未执行任何 SQL）。例如：
#     block_compile_check.sh backup/mor_platform-full.sql platform-exec.jar /tmp/b138 \
#       dormant=db/platform-support/registration-block138.sql active=release/sql/61-platform-block138-activate.sql \
#       deactivated=release/sql/62-platform-block138-deactivate.sql rolled-back=release/sql/69-platform-block138-rollback.sql
# 环境变量：BLOCK=138（统计 route_id 900000000000<BLOCK>001—999）、MYSQL_IMAGE（默认 mysql:8.4.11，应与 mor-test 同一镜像 ID）、
#   KEEP_CONTAINER=1 保留容器、JAVA_BIN（默认 JDK 25）。
# 备份获取（mor-test，只读）：mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines=0 mor_platform。
# 本地验证可用：mysqldump ... mor_platform_v2e2e（导入时库名统一改为 mor_platform，见下）。
# 退出码：任一步编译失败或 SQL 失败即非 0；输出 <工作目录>/compile.log 与每步导出 export-<标签>/。
# 平台 JAR 须是即将发布的候选版本（编译规则随版本变化）。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
dump="${1:?mor_platform dump}"; jar="${2:?platform jar or dir}"; work="${3:?work dir}"; shift 3
BLOCK="${BLOCK:-138}"; IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"
JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="block${BLOCK}-compile-$$"; pwd_=throwaway
mkdir -p "$work"; log="$work/compile.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; }
trap cleanup EXIT

if [[ -d "$jar" ]]; then jardir="$jar"; else
  jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar"); fi
[[ -d "$jardir/BOOT-INF/classes" ]] || { echo "not a Spring Boot JAR: $jar" >&2; exit 2; }

say "start $IMAGE --network none as $c"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pwd_ "$IMAGE" \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done  # 等最终实例（初始化阶段的临时实例不监听 3306）
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pwd_ mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
sql() { docker exec "$c" sh -c "MYSQL_PWD=$pwd_ mysql -uroot --default-character-set=utf8mb4 -N -B mor_platform -e \"$1\""; }
docker exec "$c" sh -c "MYSQL_PWD=$pwd_ mysql -uroot -e 'CREATE DATABASE mor_platform DEFAULT CHARACTER SET utf8mb4'"
say "import $dump"
# 备份里的 USE / CREATE DATABASE 统一指向 mor_platform（便于用 *_v2e2e 等本地库验证）
sed -E -e 's/^(USE|CREATE DATABASE( \/\*![0-9]+ IF NOT EXISTS\*\/)?) `[^`]+`/\1 `mor_platform`/' "$dump" \
  | docker exec -i "$c" sh -c "MYSQL_PWD=$pwd_ mysql -uroot --default-character-set=utf8mb4 mor_platform"

compile() {
  local label=$1 out="$work/export-$1"; mkdir -p "$out"
  sql "SELECT target_id,target_service_code,discovery_service_name,channel,status FROM t_gateway_target" > "$out/targets.tsv"
  sql "SELECT route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status FROM t_gateway_route" > "$out/routes.tsv"
  sql "SELECT permission_id,permission_code FROM t_permission" > "$out/permissions.tsv"
  sql "SELECT route_id,header_name,header_use FROM t_gateway_route_header" > "$out/headers.tsv"
  sql "SELECT route_id,parameter_name FROM t_gateway_route_query_param" > "$out/queries.tsv"
  sql "SELECT route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit FROM t_gateway_guard_rule" > "$out/guards.tsv"
  say "compile: $label"
  "$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" \
    "900000000000${BLOCK}001" "900000000000${BLOCK}999" | tee -a "$log"
  return "${PIPESTATUS[0]}"
}

compile baseline
for stepdef in "$@"; do
  label="${stepdef%%=*}"; file="${stepdef#*=}"
  say "apply $label: $file"
  docker exec -i "$c" sh -c "MYSQL_PWD=$pwd_ mysql -uroot --default-character-set=utf8mb4 mor_platform" < "$file" 2>&1 | tee -a "$log"
  [[ "${PIPESTATUS[0]}" == 0 ]] || { say "SQL FAILED: $label"; exit 1; }
  compile "$label"
done
say "block rows: $(sql "SELECT CONCAT_WS(' ', (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000${BLOCK}001 AND 900000000000${BLOCK}999), (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000${BLOCK}001 AND 900000000000${BLOCK}999), (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000${BLOCK}001 AND 900000000000${BLOCK}999), (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000${BLOCK}001 AND 900000000000${BLOCK}999))") (permission resource route guard)"
say "ALL STEPS COMPILED"
