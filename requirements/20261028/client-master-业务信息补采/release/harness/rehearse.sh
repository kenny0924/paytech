#!/usr/bin/env bash
# REQ-12 SQL 离线演练：mor-test mor_client_master 只读导出 → 本机 mor-local MySQL（13306）临时库 rq12_mt → 预检 / DDL / DML DRAFT / ACTIVE / 重跑 /
# 回滚拒绝分支（临时库内把一个申请的 schema_id 指向 v3）/ 回滚 / 再执行，最后与全新建库 rq12_fresh（候选 schema.sql + initial-data.sql）逐行比较配置表。
# 只读取 mor-test；所有写入都在本机临时库。用法：rehearse.sh <只读导出 .sql> <输出目录>
set -euo pipefail
dump="$1"; out="$2"; mkdir -p "$out"
rel="$(cd "$(dirname "$0")/.." && pwd)"; sql="$rel/sql"
src=/Users/kenny/Desktop/Workspace/Code-MoR-rq12-rel/paytech/client-master/docs/database/sql
root() { MYSQL_PWD="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//')" \
  /opt/homebrew/opt/mysql-client@8.0/bin/mysql -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4 "$@"; }
# step <名称> <库> <SQL 文件> [会话前缀 SQL] [mysql 额外参数...]：文件经 stdin 执行，记录退出码与首个 ERROR / STOP
step() { local name="$1" db="$2" file="$3" prefix="${4:-}"; shift 4 2>/dev/null || shift $#; set +e
  { [[ -n "$prefix" ]] && echo "$prefix"; cat "$file"; } | root -t "$@" "$db" > "$out/$name.out" 2>&1; local rc=$?; set -e
  echo "$name rc=$rc $(grep -m1 -E 'ERROR|STOP' "$out/$name.out" || true)" | tee -a "$out/summary.txt"; }
: > "$out/summary.txt"
root -e "DROP DATABASE IF EXISTS rq12_mt; DROP DATABASE IF EXISTS rq12_fresh; CREATE DATABASE rq12_mt CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE rq12_fresh CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
root rq12_mt < <(sed -e '/^CREATE DATABASE/d' -e '/^USE /d' "$dump")
step 01-precheck rq12_mt "$sql/00-client-master-bizinfo-precheck.sql" "SET SESSION TRANSACTION READ ONLY;"
step 02-dml-before-ddl rq12_mt "$sql/20-client-master-bizinfo-v3.sql"
step 03-ddl rq12_mt "$sql/10-client-master-bizinfo-ddl.sql"
step 04-ddl-rerun rq12_mt "$sql/10-client-master-bizinfo-ddl.sql"
step 05-dml-draft rq12_mt "$sql/20-client-master-bizinfo-v3.sql" "SET @cm_bizinfo_status='DRAFT';"
step 06-verify-draft rq12_mt "$sql/30-client-master-bizinfo-verify.sql" "SET SESSION TRANSACTION READ ONLY;"
step 07-dml-active rq12_mt "$sql/20-client-master-bizinfo-v3.sql"
step 08-dml-active-rerun rq12_mt "$sql/20-client-master-bizinfo-v3.sql"
step 09-verify-active rq12_mt "$sql/30-client-master-bizinfo-verify.sql" "SET SESSION TRANSACTION READ ONLY;"
app=$(root -N rq12_mt -e "SELECT application_id FROM t_client_application WHERE status='DRAFT' ORDER BY application_id LIMIT 1")
orig=$(root -N rq12_mt -e "SELECT schema_id FROM t_client_application WHERE application_id=$app")
root rq12_mt -e "UPDATE t_client_application SET schema_id=9281200000900301 WHERE application_id=$app"
step 10-rollback-refused rq12_mt "$sql/90-client-master-bizinfo-v3-rollback.sql"
step 11-ddl-rollback-refused rq12_mt "$sql/91-client-master-bizinfo-ddl-rollback.sql"
root rq12_mt -e "UPDATE t_client_application SET schema_id=$orig WHERE application_id=$app"
step 12-rollback rq12_mt "$sql/90-client-master-bizinfo-v3-rollback.sql"
step 13-rollback-rerun rq12_mt "$sql/90-client-master-bizinfo-v3-rollback.sql"
step 14-ddl-rollback rq12_mt "$sql/91-client-master-bizinfo-ddl-rollback.sql"
step 15-ddl-again rq12_mt "$sql/10-client-master-bizinfo-ddl.sql"
step 16-dml-again rq12_mt "$sql/20-client-master-bizinfo-v3.sql"
root rq12_fresh < "$src/client-master-schema.sql"; root rq12_fresh < "$src/client-master-initial-data.sql"
for db in rq12_mt rq12_fresh; do
  root -N "$db" -e "SELECT catalog_item_id,catalog_code,item_code,jurisdiction_code,label_key,client_types,display_order,status FROM t_catalog_item ORDER BY 1" > "$out/$db.catalog.tsv"
  root -N "$db" -e "SELECT schema_id,jurisdiction_code,party_type,role_type,organization_type_code,version_no,status,active_slot FROM t_onboarding_schema ORDER BY 1" > "$out/$db.schema.tsv"
  root -N "$db" -e "SELECT schema_field_id,schema_id,section,display_group,field_key,widget_code,catalog_code,requirement_level,requirement_group,applies_to_subject,validation_pattern,min_length,max_length,visible_when,label_key,help_key,display_order FROM t_onboarding_schema_field ORDER BY 1" > "$out/$db.field.tsv"
  for t in t_client_business_profile t_onboarding_schema_field t_catalog_item t_onboarding_schema; do root -N "$db" -e "SHOW CREATE TABLE $t" | cut -f2- | sed 's/ AUTO_INCREMENT=[0-9]*//'; done > "$out/$db.ddl"
done
for f in catalog.tsv schema.tsv field.tsv ddl; do echo "compare $f: $(diff "$out/rq12_mt.$f" "$out/rq12_fresh.$f" | grep -c '^[<>]') differing lines" | tee -a "$out/summary.txt"; done
root -e "DROP DATABASE rq12_mt; DROP DATABASE rq12_fresh;"
echo "scratch databases dropped" | tee -a "$out/summary.txt"
