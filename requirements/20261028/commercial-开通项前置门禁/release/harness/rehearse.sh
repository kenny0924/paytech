#!/usr/bin/env bash
# REQ-01 开通项前置门禁 · 发布 SQL 演练：断网一次性 MySQL（与 mor-test 同一镜像）导入 mor-test 只读导出的 mor_platform 与 mor_commercial，
# 按 runbook 顺序执行正向脚本、应失败的保护分支，再逆序回退，比对结构与数据回到导出状态；
# 块 141 每一步后用候选 Platform JAR 内的生产 GatewayConfigurationCompiler 编译 Gateway 快照（BlockSnapshotCheck.java）。
# 改写自卡 BIN 库发布 release/harness/rehearse.sh（块区间改为 141，增加 mor_commercial 迁移 / 回滚 / 重跑）。
# 用法：rehearse.sh <mor_platform 导出 .sql.gz> <mor_commercial 导出 .sql.gz> <Platform exec JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL、保护分支或编译不符合预期。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../sql"
pf_dump="${1:?mor_platform dump}"; cm_dump="${2:?mor_commercial dump}"; jar="${3:?platform jar}"; work="${4:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="gate-rehearse-$$"; pw=throwaway; mkdir -p "$work"; log="$work/rehearse.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; }
trap cleanup EXIT
jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar")
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform jar $(shasum -a 256 "$jar" | cut -c1-16); dumps $(shasum -a 256 "$pf_dump" | cut -c1-16) $(shasum -a 256 "$cm_dump" | cut -c1-16)"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
gunzip -c "$pf_dump" | mysqlx ""; say "imported $(basename "$pf_dump")"
gunzip -c "$cm_dump" | mysqlx ""; say "imported $(basename "$cm_dump")"

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
  "$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" 900000000000141001 900000000000141999 | tee -a "$log"
  return "${PIPESTATUS[0]}"
}
step() {  # step <标签> <库> <sql 文件> [expect-fail]：执行并计时，输出保存到 <工作目录>/<标签>.out
  local label=$1 db=$2 file="$sqld/$3" expect=${4:-ok} t0 t1 rc=0
  t0=$(python3 -c 'import time;print(time.time())')
  mysqlx "-t $db" < "$file" > "$work/$label.out" 2>&1 || rc=$?
  t1=$(python3 -c 'import time;print(time.time())')
  say "$label ($3) exit=$rc expect=$expect $(python3 -c "print(f'{$t1-$t0:.2f}s')")"
  grep -v "Using a password" "$work/$label.out" >> "$log" || true
  if [[ $expect == ok && $rc != 0 ]]; then say "SQL FAILED: $label"; exit 1; fi
  if [[ $expect == fail && $rc == 0 ]]; then say "PROTECTION MISSING: $label succeeded"; exit 1; fi
}
canon mor_platform "$work/pf-before.canon"
canon mor_commercial "$work/cm-before.canon"

say "===== mor_commercial forward"
step c00-precheck mor_commercial mor_commercial-precheck.sql
step c10-migrate mor_commercial mor_commercial-migrate.sql
step c11-verify-after mor_commercial mor_commercial-verify-after.sql
step c12-migrate-again mor_commercial mor_commercial-migrate.sql
step c13-verify-after-again mor_commercial mor_commercial-verify-after.sql
canon mor_commercial "$work/cm-migrated.canon"

say "===== mor_platform forward"
compile baseline
step p00-precheck mor_platform mor_platform-block-141-precheck.sql
step p10-register mor_platform mor_platform-block-141.sql;                compile dormant
step p13-verify-dormant mor_platform mor_platform-block-141-verify.sql
step p10-register-again mor_platform mor_platform-block-141.sql fail
step p11-activate mor_platform mor_platform-block-141-activate.sql;       compile active
step p13-verify-active mor_platform mor_platform-block-141-verify.sql
step p19-rollback-while-active mor_platform mor_platform-block-141-rollback.sql fail; compile still-active

say "===== rollback (reverse order)"
step p12-deactivate mor_platform mor_platform-block-141-deactivate.sql;   compile deactivated
step p19-rollback mor_platform mor_platform-block-141-rollback.sql;       compile rolled-back
step c90-rollback mor_commercial mor_commercial-rollback.sql
step c91-precheck-after-rollback mor_commercial mor_commercial-precheck.sql
step c92-rollback-again mor_commercial mor_commercial-rollback.sql
canon mor_commercial "$work/cm-rolledback.canon"
step c93-migrate-after-rollback mor_commercial mor_commercial-migrate.sql
canon mor_commercial "$work/cm-remigrated.canon"

canon mor_platform "$work/pf-final.canon"
diff "$work/pf-before.canon" "$work/pf-final.canon" > "$work/pf-before-vs-final.diff" || true
diff "$work/cm-before.canon" "$work/cm-rolledback.canon" > "$work/cm-before-vs-rolledback.diff" || true
diff "$work/cm-migrated.canon" "$work/cm-remigrated.canon" > "$work/cm-migrated-vs-remigrated.diff" || true
say "platform before vs final: $(grep -c '^[<>]' "$work/pf-before-vs-final.diff" || true) differing lines"
say "commercial before vs rolled back: $(grep -c '^[<>]' "$work/cm-before-vs-rolledback.diff" || true) differing lines (expected: only the two kept consumed-event tables)"
say "commercial migrated vs re-migrated: $(grep -c '^[<>]' "$work/cm-migrated-vs-remigrated.diff" || true) differing lines (expected: update_time unchanged, 0)"
say "DONE"
