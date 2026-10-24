#!/usr/bin/env bash
# 运营控制台文件上传入口 · mor-test SQL 执行包装（阶段 7，主会话执行；root；客户端 utf8mb4；输出写入备份目录）。写法同多语言管理发布包。
# 用法：apply_sql.sh <备份目录> <步骤>
#   backup                           ← mysqldump --single-transaction mor_platform 到 <备份目录>/mor_platform-full.sql（只读）
#   precheck                         ← sql/00-precheck.sql（只读）
#   reg-dry / reg                    ← sql/10-registration-block136.sql（休眠注册 64 行；任何镜像组合下无影响）
#   activate-dry / activate          ← sql/20-activate-block136.sql（3 个新镜像都 healthy 之后）
#   postcheck                        ← sql/99-postcheck.sql（只读）
#   deactivate-dry / deactivate      ← sql/21-deactivate-block136.sql（回滚：停用入口，注册行保留）
#   rollback-reg-dry / rollback-reg  ← sql/90-rollback-block136.sql（回滚：按 ID 段删除注册行）
# *-dry：把脚本里的 COMMIT 换成 ROLLBACK 后执行（看到影响行数，不提交）。每步输出 <备份目录>/sql-<步骤>-<时间>.out；口令只经 MYSQL_PWD 传入容器。
# 演练用覆盖（阶段 7 不要设置）：FU_REL_MYSQL_CONTAINER（缺省 mor-test-mysql-1）、FU_REL_MYSQL_PWD（缺省取 test-environment/.env.test）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
root="${MOR_ROOT:-/Users/kenny/Desktop/Workspace/Code-MoR}"
dir="${1:?backup dir}"; step="${2:?step}"
container="${FU_REL_MYSQL_CONTAINER:-mor-test-mysql-1}"
root_pw="${FU_REL_MYSQL_PWD:-$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$root/test-environment/.env.test" | cut -d= -f2-)}"
umask 077
mkdir -p "$dir"
if [[ "$step" == backup ]]; then
  docker exec -e MYSQL_PWD="$root_pw" "$container" mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 --databases mor_platform > "$dir/mor_platform-full.sql"
  shasum -a 256 "$dir/mor_platform-full.sql"; exit 0
fi
[[ -s "$dir/mor_platform-full.sql" ]] || { echo "backup missing: run '$0 $dir backup' first" >&2; exit 1; }
dry=0
case "$step" in
  precheck) file=00-precheck.sql ;;
  reg-dry) file=10-registration-block136.sql; dry=1 ;;        reg) file=10-registration-block136.sql ;;
  activate-dry) file=20-activate-block136.sql; dry=1 ;;       activate) file=20-activate-block136.sql ;;
  postcheck) file=99-postcheck.sql ;;
  deactivate-dry) file=21-deactivate-block136.sql; dry=1 ;;   deactivate) file=21-deactivate-block136.sql ;;
  rollback-reg-dry) file=90-rollback-block136.sql; dry=1 ;;   rollback-reg) file=90-rollback-block136.sql ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
file="$here/sql/$file"
out="$dir/sql-$step-$(date -u +%H%M%SZ).out"
{ if [[ $dry == 1 ]]; then sed 's/^COMMIT;$/ROLLBACK;/' "$file"; else cat "$file"; fi; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" "$container" mysql -uroot --default-character-set=utf8mb4 -t mor_platform \
  > "$out" 2>&1 || { cat "$out" >&2; exit 1; }
cat "$out"
