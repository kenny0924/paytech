#!/usr/bin/env bash
# 商业关系快照与真实解析接入 · mor-test SQL 执行包装（阶段 7，主会话执行；root；客户端 utf8mb4；输出写入备份目录）。写法同文件上传入口发布包。
# 用法：apply_sql.sh <备份目录> <步骤> [--apply]
#   缺省为演练（dry）：纯事务步骤把 COMMIT 换成 ROLLBACK 后执行（看影响行数，不提交）；含 DDL / 隐式提交的步骤演练时只打印将执行的文件，
#   不连库。只有带 --apply 才真正执行。备份与只读核对步骤不需要 --apply。
# 步骤（发布顺序见 release.md；库名固定在映射表里，不能传错库）：
#   backup                       mysqldump --single-transaction 六个库到 <备份目录>/<库>-full.sql（只读）
#   precheck / postcheck         sql/00-precheck.sql / sql/99-postcheck.sql（只读，跨库）
#   cmp-ddl  cmp-backfill        mor_compliance      10（DDL）/ 11（事务）
#   cm-ddl   cm-seed  cm-data    mor_client_master   20（DDL）/ 21（事务，D-1）/ 22（含 ALTER）
#   cm-ac11                      mor_client_master   23（事务，D-3）
#   com-fallback                 mor_commercial      30（事务，仅 D-2 选择 SQL 兜底时）
#   acq-ddl  acq-platform        mor_acquiring       40（DDL）/ 41（事务 + ALTER 注释）
#   risk                         mor_risk_decision   50（事务 + ALTER 注释）
#   plt-reg  plt-activate  plt-deactivate  plt-rollback   mor_platform   60 / 61 / 62 / 69（事务）
#   rb-cmp-backfill rb-cmp-ddl rb-cm-ac11 rb-cm-data rb-cm-seed rb-cm-ddl rb-com-fallback rb-acq-platform rb-acq-ddl rb-risk
#                                各库回滚 80—89（含 DDL 的同样只在 --apply 时执行）
# 每步输出 <备份目录>/sql-<步骤>[-dry]-<时间>.out；口令只经 MYSQL_PWD 传入容器，不打印。
# 演练用覆盖（阶段 7 不要设置）：RELSNAP_MYSQL_CONTAINER（缺省 mor-test-mysql-1）、RELSNAP_MYSQL_PWD（缺省取 test-environment/.env.test）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
root="${MOR_ROOT:-/Users/kenny/Desktop/Workspace/Code-MoR}"
dir="${1:?backup dir}"; step="${2:?step}"; mode="${3:-dry}"
container="${RELSNAP_MYSQL_CONTAINER:-mor-test-mysql-1}"
root_pw="${RELSNAP_MYSQL_PWD:-$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$root/test-environment/.env.test" | cut -d= -f2-)}"
dbs=(mor_compliance mor_client_master mor_commercial mor_acquiring mor_risk_decision mor_platform)
umask 077
mkdir -p "$dir"
if [[ "$step" == backup ]]; then
  for db in "${dbs[@]}"; do
    docker exec -e MYSQL_PWD="$root_pw" "$container" mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF \
      --default-character-set=utf8mb4 --databases "$db" > "$dir/$db-full.sql"
  done
  (cd "$dir" && shasum -a 256 ./*-full.sql | tee SHA256SUMS); exit 0
fi
for db in "${dbs[@]}"; do
  [[ -s "$dir/$db-full.sql" ]] || { echo "backup missing ($db): run '$0 $dir backup' first" >&2; exit 1; }
done
# 步骤 → 库 | 文件 | 类型（ro 只读、tx 纯事务可演练、ddl 含 DDL 或隐式提交、只在 --apply 时执行）
case "$step" in
  precheck)        spec="mysql|00-precheck.sql|ro" ;;
  postcheck)       spec="mysql|99-postcheck.sql|ro" ;;
  cmp-ddl)         spec="mor_compliance|10-compliance-ddl.sql|ddl" ;;
  cmp-backfill)    spec="mor_compliance|11-compliance-backfill.sql|tx" ;;
  cm-ddl)          spec="mor_client_master|20-client-master-ddl.sql|ddl" ;;
  cm-seed)         spec="mor_client_master|21-client-master-ae001-seed.sql|tx" ;;
  cm-data)         spec="mor_client_master|22-client-master-data.sql|ddl" ;;
  cm-ac11)         spec="mor_client_master|23-client-master-ac11-profile-fix.sql|tx" ;;
  com-fallback)    spec="mor_commercial|30-commercial-payfac-agreement-fallback.sql|tx" ;;
  acq-ddl)         spec="mor_acquiring|40-acquiring-ddl.sql|ddl" ;;
  acq-platform)    spec="mor_acquiring|41-acquiring-remove-platform.sql|ddl" ;;
  risk)            spec="mor_risk_decision|50-risk-remove-platform.sql|ddl" ;;
  plt-reg)         spec="mor_platform|60-platform-block137-registration.sql|tx" ;;
  plt-activate)    spec="mor_platform|61-platform-block137-activate.sql|tx" ;;
  plt-deactivate)  spec="mor_platform|62-platform-block137-deactivate.sql|tx" ;;
  plt-rollback)    spec="mor_platform|69-platform-block137-rollback.sql|tx" ;;
  rb-cmp-backfill) spec="mor_compliance|80-rollback-compliance-backfill.sql|tx" ;;
  rb-cmp-ddl)      spec="mor_compliance|81-rollback-compliance-ddl.sql|ddl" ;;
  rb-cm-ac11)      spec="mor_client_master|82-rollback-client-master-ac11-profile-fix.sql|tx" ;;
  rb-cm-data)      spec="mor_client_master|83-rollback-client-master-data.sql|tx" ;;
  rb-cm-seed)      spec="mor_client_master|84-rollback-client-master-seed.sql|tx" ;;
  rb-cm-ddl)       spec="mor_client_master|85-rollback-client-master-ddl.sql|ddl" ;;
  rb-com-fallback) spec="mor_commercial|86-rollback-commercial-fallback.sql|tx" ;;
  rb-acq-platform) spec="mor_acquiring|87-rollback-acquiring-platform.sql|ddl" ;;
  rb-acq-ddl)      spec="mor_acquiring|88-rollback-acquiring-ddl.sql|ddl" ;;
  rb-risk)         spec="mor_risk_decision|89-rollback-risk.sql|ddl" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
IFS='|' read -r db file kind <<< "$spec"
file="$here/sql/$file"
[[ "$mode" == dry || "$mode" == --apply ]] || { echo "third argument must be --apply (or omitted for dry run)" >&2; exit 2; }
if [[ "$kind" == ddl && "$mode" != --apply ]]; then
  echo "[$step] $file contains DDL / implicit commits: dry run does not connect; statements that --apply would run:"
  grep -vE '^\s*--' "$file" | sed '/^\s*$/d'
  exit 0
fi
suffix=""; [[ "$kind" == tx && "$mode" != --apply ]] && suffix="-dry"
out="$dir/sql-$step$suffix-$(date -u +%H%M%SZ).out"
{ if [[ -n "$suffix" ]]; then sed 's/^COMMIT;$/ROLLBACK;/' "$file"; else cat "$file"; fi; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" "$container" mysql -uroot --default-character-set=utf8mb4 -t "$db" \
  > "$out" 2>&1 || { cat "$out" >&2; exit 1; }
cat "$out"
