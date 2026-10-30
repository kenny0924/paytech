#!/usr/bin/env bash
# payout S1（收款账户与余额解释）· 发布 SQL 离线演练：断网一次性 MySQL（与 mor-test 同一镜像 mysql:8.4.11）导入 mor-test 只读导出的
# mor_platform 与 mor_vault，按 runbook 顺序执行：建库与账号（10，含守卫与重跑）→ mor_payout DDL（12，含重跑拒绝、有数据时回滚拒绝、
# 回滚 → 重建一致）→ Vault DML（20，含重跑拒绝、有记录时回滚拒绝）→ 注册块 147（休眠 → 激活 → 重复激活 → 激活中回滚拒绝 → 停用 → 回滚），
# 每个块状态用指定 Platform JAR 内的生产 GatewayConfigurationCompiler 编译快照并序列化（BlockSnapshotCheck.java），激活后的响应体再用
# 指定 Gateway JAR 的生产解析解码（GatewaySnapshotDecodeCheck.java）。最后比对 mor_platform / mor_vault 与导出逐行差异。
# 改写自 requirements/20261029/risk-decision-限额档位/release/harness/rehearse.sh（块区间 147；risk DDL 换成 payout 建库 + DDL + Vault）。
# 用法：rehearse.sh <mor_platform 导出 .sql.gz> <mor_vault 导出 .sql.gz> <Platform exec JAR> <Gateway exec JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL / 保护 / 编译 / 解码不符合预期，或响应体 ≥ SNAPSHOT_CAP（缺省 4194304）。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../sql"
pf_dump="${1:?mor_platform dump}"; vt_dump="${2:?mor_vault dump}"; jar="${3:?platform jar}"; gjar="${4:?gateway jar}"; work="${5:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="payout-s1-rehearse-$$"; pw=throwaway; apppw="rehearsal_payout_app_pw_0123456789"
mkdir -p "$work"; chmod 700 "$work"; log="$work/rehearse.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; rm -rf "$work/platform-jar" "$work/gateway-jar"; }
trap cleanup EXIT
jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar")
gdir="$work/gateway-jar"; rm -rf "$gdir"; mkdir -p "$gdir"; (cd "$gdir" && unzip -q "$gjar")
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform jar $(shasum -a 256 "$jar" | cut -c1-16); gateway jar $(shasum -a 256 "$gjar" | cut -c1-16); dumps $(shasum -a 256 "$pf_dump" | cut -c1-16) / $(shasum -a 256 "$vt_dump" | cut -c1-16)"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
say "container network: $(docker inspect "$c" --format '{{.HostConfig.NetworkMode}}')"
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
gunzip -c "$pf_dump" | mysqlx ""; gunzip -c "$vt_dump" | mysqlx ""; say "imported $(basename "$pf_dump") $(basename "$vt_dump")"

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
  local res; res=$("$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" 900000000000147001 900000000000147999 "${SNAPSHOT_CAP:-4194304}" "$work/snapshot-$label.json" 2>&1 | tail -2 || true)
  rm -rf "$out"
  say "compile $label: $(tr '\n' ' ' <<<"$res")"
  [[ $res == *"COMPILE OK"* && $res != *"SIZE EXCEEDED"* ]] || { say "COMPILE NOT OK: $label"; exit 1; }
  res=$("$JAVA_BIN" -cp "$gdir/BOOT-INF/classes:$gdir/BOOT-INF/lib/*" "$here/GatewaySnapshotDecodeCheck.java" "$work/snapshot-$label.json" 2>&1 | tail -1 || true)
  say "gateway-decode $label: $res"
  [[ $res == *"DECODE+COMPILE OK"* ]] || { say "GATEWAY DECODE NOT OK: $label"; exit 1; }
}
step() {  # step <库|-> <标签> <sql 文件（相对 sql/ 或绝对）> [ok|fail] [前置 SQL]
  local db=$1 label=$2 file=$3 expect=${4:-ok} pre=${5:-} t0 t1 rc=0
  [[ $file == /* ]] || file="$sqld/$file"; [[ $db == - ]] && db=""
  t0=$(python3 -c 'import time;print(time.time())')
  { [[ -n $pre ]] && printf '%s\n' "$pre"; cat "$file"; } | mysqlx "-t $db" > "$work/$label.out" 2>&1 || rc=$?
  t1=$(python3 -c 'import time;print(time.time())')
  say "$label ($(basename "$file") @ ${db:-<none>}) exit=$rc expect=$expect $(python3 -c "print(f'{$t1-$t0:.2f}s')")"
  grep -v "Using a password" "$work/$label.out" | sed "s/$apppw/<app-password>/g" >> "$log" || true
  sed -i '' "s/$apppw/<app-password>/g" "$work/$label.out" 2>/dev/null || true
  if [[ $expect == ok && $rc != 0 ]]; then say "SQL FAILED: $label"; exit 1; fi
  if [[ $expect == fail && $rc == 0 ]]; then say "PROTECTION MISSING: $label succeeded"; exit 1; fi
}
asapp() {  # asapp <标签> <SQL> <ok|fail>：用 payout_app 账号执行，验证只有 DML
  local label=$1 sql=$2 expect=$3 rc=0
  docker exec -i "$c" sh -c "MYSQL_PWD=$apppw mysql -upayout_app -t mor_payout" <<<"$sql" > "$work/$label.out" 2>&1 || rc=$?
  say "$label (payout_app) exit=$rc expect=$expect"; grep -v "Using a password" "$work/$label.out" >> "$log" || true
  if [[ $expect == ok && $rc != 0 ]]; then say "GRANT CHECK FAILED: $label"; exit 1; fi
  if [[ $expect == fail && $rc == 0 ]]; then say "GRANT TOO WIDE: $label succeeded"; exit 1; fi
}
cnt() { grep -c '^[<>]' "$1" || true; }

canon mor_platform "$work/pf-before.canon"
canon mor_vault "$work/vt-before.canon"

say "===== A. mor_payout 建库与账号、DDL"
step - a01-precheck 01-mysql-payout-database-precheck.sql
step - a10-create-without-password 10-mysql-payout-database-account.sql fail
step - a10-create-bad-password 10-mysql-payout-database-account.sql fail "SET @payout_app_password = 'short';"
step - a10-create 10-mysql-payout-database-account.sql ok "SET @payout_app_password = '$apppw';"
step - a10-create-again 10-mysql-payout-database-account.sql ok "SET @payout_app_password = '$apppw';"
step - a11-verify-empty 11-mysql-payout-database-verify.sql
step mor_payout a12-ddl 12-mor_payout-ddl.sql
step mor_payout a13-verify 13-mor_payout-verify.sql
canon mor_payout "$work/po-created.canon"
step mor_payout a12-ddl-again 12-mor_payout-ddl.sql fail
canon mor_payout "$work/po-after-rerun.canon"
asapp a14-app-dml "START TRANSACTION; SELECT COUNT(*) FROM t_beneficiary_account; INSERT INTO t_compensation_event (id,event_key,event_type,sub_event_type) VALUES (1,'k','BENEFICIARY_NOTIFICATION','-'); ROLLBACK;" ok
asapp a15-app-ddl-denied "CREATE TABLE t_x (id BIGINT PRIMARY KEY);" fail
asapp a16-app-other-db-denied "SELECT COUNT(*) FROM mor_platform.t_permission;" fail
step mor_payout a20-fixture-row "$here/fixture-payout-row.sql"
step mor_payout a21-rollback-refused 91-mor_payout-ddl-rollback.sql fail
q mor_payout "DELETE FROM t_beneficiary_account_event WHERE id = 880000000901"; say "fixture row deleted"
step - a22-db-rollback-refused 92-mysql-payout-database-rollback.sql fail
step mor_payout a23-ddl-rollback 91-mor_payout-ddl-rollback.sql
step mor_payout a24-ddl-rollback-again 91-mor_payout-ddl-rollback.sql
step mor_payout a25-ddl-recreate 12-mor_payout-ddl.sql
canon mor_payout "$work/po-recreated.canon"
step mor_payout a26-ddl-rollback-for-db 91-mor_payout-ddl-rollback.sql
step - a27-db-rollback 92-mysql-payout-database-rollback.sql
step - a28-db-rollback-again 92-mysql-payout-database-rollback.sql
step - a29-precheck-after-rollback 01-mysql-payout-database-precheck.sql

say "===== B. mor_vault"
step mor_vault b02-precheck 02-mor_vault-payout-precheck.sql
step mor_vault b20-forward 20-mor_vault-payout.sql
step mor_vault b21-verify 21-mor_vault-payout-verify.sql
step mor_vault b20-forward-again 20-mor_vault-payout.sql fail
step mor_vault b22-fixture-record "$here/fixture-vault-record.sql"
step mor_vault b23-rollback-refused 90-mor_vault-payout-rollback.sql fail
q mor_vault "DELETE FROM t_vault_record WHERE record_id = 880000000903"; say "fixture record deleted"
step mor_vault b24-rollback 90-mor_vault-payout-rollback.sql
step mor_vault b25-rollback-again 90-mor_vault-payout-rollback.sql
step mor_vault b26-precheck-after-rollback 02-mor_vault-payout-precheck.sql
canon mor_vault "$work/vt-final.canon"

say "===== C. 注册块 147"
compile baseline
step mor_platform c03-precheck 03-mor_platform-block-147-precheck.sql
step mor_platform c32-activate-before-register 32-mor_platform-block-147-activate.sql fail
step mor_platform c30-register 30-mor_platform-block-147.sql;                  compile dormant
step mor_platform c31-verify-dormant 31-mor_platform-block-147-verify.sql
step mor_platform c30-register-again 30-mor_platform-block-147.sql fail
step mor_platform c32-activate 32-mor_platform-block-147-activate.sql;         compile active
step mor_platform c31-verify-active 31-mor_platform-block-147-verify.sql
step mor_platform c32-activate-again 32-mor_platform-block-147-activate.sql;   compile active-again
step mor_platform c93-rollback-while-active 93-mor_platform-block-147-rollback.sql fail
step mor_platform c33-deactivate 33-mor_platform-block-147-deactivate.sql;     compile deactivated
step mor_platform c31-verify-deactivated 31-mor_platform-block-147-verify.sql
step mor_platform c33-deactivate-again 33-mor_platform-block-147-deactivate.sql
step mor_platform c93-rollback 93-mor_platform-block-147-rollback.sql;         compile rolled-back
step mor_platform c93-rollback-again 93-mor_platform-block-147-rollback.sql
canon mor_platform "$work/pf-final.canon"

diff "$work/pf-before.canon" "$work/pf-final.canon" > "$work/pf-before-vs-final.diff" || true
diff "$work/vt-before.canon" "$work/vt-final.canon" > "$work/vt-before-vs-final.diff" || true
diff "$work/po-created.canon" "$work/po-after-rerun.canon" > "$work/po-created-vs-rerun.diff" || true
diff "$work/po-created.canon" "$work/po-recreated.canon" > "$work/po-created-vs-recreated.diff" || true
say "mor_vault before vs final (after rollback): $(cnt "$work/vt-before-vs-final.diff") differing lines (expected 0)"
say "mor_payout created vs after failed rerun: $(cnt "$work/po-created-vs-rerun.diff") differing lines (expected 0)"
say "mor_payout created vs recreated after rollback: $(cnt "$work/po-created-vs-recreated.diff") differing lines (expected 0)"
# 块 147 激活 / 停用各把平台管理员角色 900000000000000510 的 version + 1（会话权限缓存刷新，同块 143），回滚不回退该计数——
# 允许的差异只有 t_role 中该角色这一行（version 与 update_time）。
other=$({ grep '^[<>]' "$work/pf-before-vs-final.diff" | grep -v "^[<>] INSERT INTO \`t_role\` VALUES (900000000000000510," || true; } | wc -l | tr -d ' ')
say "mor_platform before vs final (after block rollback): $(cnt "$work/pf-before-vs-final.diff") differing lines; outside PLATFORM_ADMIN role version row: $other (expected 0)"
[[ $(cnt "$work/vt-before-vs-final.diff") == 0 && $(cnt "$work/po-created-vs-rerun.diff") == 0 && $(cnt "$work/po-created-vs-recreated.diff") == 0 && $other == 0 ]] || { say "CANON MISMATCH"; exit 1; }
rm -f "$work"/*.canon
mkdir -p "$work/evidence"; cp "$work"/*.out "$work"/*.diff "$work/rehearse.log" "$work/evidence/"
for f in "$work"/snapshot-*.json; do printf "%s %s %s\n" "$(basename "$f")" "$(wc -c < "$f" | tr -d " ")" "$(shasum -a 256 "$f" | cut -c1-16)"; done > "$work/evidence/snapshot-sizes.txt"
say "DONE"
