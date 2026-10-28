#!/usr/bin/env bash
# 块 140（card：BIN 库页面重设计与号段列表）发布 SQL 演练：断网一次性 MySQL（与 mor-test 同一镜像 ID）导入 mor-test 只读备份，
# 按 runbook 顺序执行正向脚本、两个应失败的保护分支（ACTIVE 时回退被拒、重复注册被拒），再逆序回退，比对结构与数据回到备份状态；
# 每一步后用候选 Platform JAR 内的生产 GatewayConfigurationCompiler 编译 Gateway 快照（BlockSnapshotCheck.java）。
# 改写自参考数据发布 release/harness/rehearse.sh（只保留 mor_platform、块区间改为 140）。
# 用法：rehearse.sh <mor_platform 备份 .sql.gz> <Platform exec JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL、保护分支或编译不符合预期。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../sql"
pf_dump="${1:?mor_platform dump}"; jar="${2:?platform jar}"; work="${3:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="binlist-rehearse-$$"; pw=throwaway; mkdir -p "$work"; log="$work/rehearse.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; }
trap cleanup EXIT
jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar")
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform jar $(shasum -a 256 "$jar" | cut -c1-16); dump $(shasum -a 256 "$pf_dump" | cut -c1-16)"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
gunzip -c "$pf_dump" | mysqlx ""
say "imported $(basename "$pf_dump")"

canon() {  # 结构（SHOW CREATE，去掉 AUTO_INCREMENT 计数）+ 例程 + 按主键排序的逐行数据
  local db=$1 out=$2; : > "$out"
  for t in $(q "$db" "SHOW TABLES"); do q "$db" "SHOW CREATE TABLE \`$t\`" | sed -E 's/ AUTO_INCREMENT=[0-9]+//' >> "$out"; done
  q "$db" "SELECT routine_name FROM information_schema.routines WHERE routine_schema='$db' ORDER BY 1" | sed 's/^/ROUTINE /' >> "$out"
  docker exec "$c" sh -c "MYSQL_PWD=$pw mysqldump -uroot --no-create-info --skip-extended-insert --skip-dump-date --order-by-primary --skip-comments --no-tablespaces --set-gtid-purged=OFF $db" >> "$out"
}
compile() {
  local label=$1 out="$work/export-$1"; mkdir -p "$out"
  q mor_platform "SELECT target_id,target_service_code,discovery_service_name,channel,status FROM t_gateway_target" > "$out/targets.tsv"
  q mor_platform "SELECT route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status FROM t_gateway_route" > "$out/routes.tsv"
  q mor_platform "SELECT permission_id,permission_code FROM t_permission" > "$out/permissions.tsv"
  q mor_platform "SELECT route_id,header_name,header_use FROM t_gateway_route_header" > "$out/headers.tsv"
  q mor_platform "SELECT route_id,parameter_name FROM t_gateway_route_query_param" > "$out/queries.tsv"
  q mor_platform "SELECT route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit FROM t_gateway_guard_rule" > "$out/guards.tsv"
  printf '### compile %-22s ' "$label" | tee -a "$log"
  "$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" 900000000000140001 900000000000140999 | tee -a "$log"
  return "${PIPESTATUS[0]}"
}
step() {  # step <标签> <sql 文件> [expect-fail]：执行并计时，输出保存到 <工作目录>/<标签>.out
  local label=$1 file="$sqld/$2" expect=${3:-ok} t0 t1 rc=0
  t0=$(python3 -c 'import time;print(time.time())')
  mysqlx "-t mor_platform" < "$file" > "$work/$label.out" 2>&1 || rc=$?
  t1=$(python3 -c 'import time;print(time.time())')
  say "$label ($2) exit=$rc expect=$expect $(python3 -c "print(f'{$t1-$t0:.2f}s')")"
  grep -v "Using a password" "$work/$label.out" >> "$log" || true
  if [[ $expect == ok && $rc != 0 ]]; then say "SQL FAILED: $label"; exit 1; fi
  if [[ $expect == fail && $rc == 0 ]]; then say "PROTECTION MISSING: $label succeeded"; exit 1; fi
}
canon mor_platform "$work/pf-before.canon"
say "===== forward"
compile baseline
step 00-precheck 00-platform-block140-precheck.sql
step 10-register 10-platform-block140-registration.sql;      compile dormant
step 13-verify-dormant 13-platform-block140-verify.sql
step 10-register-again 10-platform-block140-registration.sql fail
step 11-activate 11-platform-block140-activate.sql;          compile active
step 13-verify-active 13-platform-block140-verify.sql
step 19-rollback-while-active 19-platform-block140-rollback.sql fail; compile still-active
say "===== rollback"
step 12-deactivate 12-platform-block140-deactivate.sql;      compile deactivated
step 19-rollback 19-platform-block140-rollback.sql;          compile rolled-back
canon mor_platform "$work/pf-final.canon"
diff "$work/pf-before.canon" "$work/pf-final.canon" > "$work/pf-before-vs-final.diff" || true
say "platform before vs final: $(grep -c '^[<>]' "$work/pf-before-vs-final.diff" || true) differing lines (see pf-before-vs-final.diff)"
say "DONE"
