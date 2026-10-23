#!/usr/bin/env bash
# 多语言管理 · mor-test SQL 执行包装（阶段 7，主会话执行；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck                         ← sql/00-precheck.sql（只读）
#   ddl                              ← sql/01-platform-ddl.sql（七表；DDL 隐式提交，无 dry；旧 Platform 运行时可执行）
#   reg-dry / reg                    ← sql/10-block135-registration.sql（休眠注册 331 行；任何镜像组合下无影响）
#   seed-dry / seed                  ← sql/11-localization-seed.sql（语言 2、应用 4）
#   files-dry / files                ← sql/12-file-policy-route.sql（新 Platform healthy 之后）
#   activate-dry / activate          ← sql/20-activate-block135.sql（5 个新镜像都 healthy 之后）
#   postcheck                        ← sql/99-postcheck.sql（只读；激活后）
#   bundles-check                    ← sql/98-bundles-check.sql（只读；首批语言包后）
#   deactivate-dry / deactivate      ← sql/21-deactivate-block135.sql（回滚）
#   rollback-files-dry / rollback-files  ← sql/90-rollback-file-policy-route.sql（回滚；有文件引用策略时 guard 拒绝）
#   retire-files-dry / retire-files      ← sql/90b-retire-file-policies.sql（回滚；有引用时用它）
#   rollback-seed-dry / rollback-seed    ← sql/91-rollback-seed.sql（回滚；t_i18n_* 业务表为空时）
#   rollback-reg-dry / rollback-reg      ← sql/92-rollback-registration.sql（回滚；彻底移除注册）
#   rollback-ddl                     ← sql/93-rollback-ddl.sql（回滚；删七表，隐式提交，无 dry）
# *-dry 不提交：断开即回滚；非 dry 在末尾追加 COMMIT。改数文件都有 DATABASE() = 'mor_platform' 守卫与 guard 行：不符时 guard
# 子查询多行报错，mysql 非零退出，未提交事务随断开回滚。每步输出 <备份目录>/sql-<步骤>-<时间>.out；口令只经 MYSQL_PWD 传入容器，不回显。
# 演练用覆盖（阶段 7 不要设置）：I18N_REL_MYSQL_CONTAINER（缺省 mor-test-mysql-1）、I18N_REL_MYSQL_PWD（缺省取 test-environment/.env.test）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform-full.sql" ]] || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
container="${I18N_REL_MYSQL_CONTAINER:-mor-test-mysql-1}"
commit=0; db=mor_platform
case "$step" in
  precheck) db=mysql; file=00-precheck.sql ;;
  ddl) file=01-platform-ddl.sql ;;
  reg-dry) file=10-block135-registration.sql ;;            reg) file=10-block135-registration.sql; commit=1 ;;
  seed-dry) file=11-localization-seed.sql ;;               seed) file=11-localization-seed.sql; commit=1 ;;
  files-dry) file=12-file-policy-route.sql ;;              files) file=12-file-policy-route.sql; commit=1 ;;
  activate-dry) file=20-activate-block135.sql ;;           activate) file=20-activate-block135.sql; commit=1 ;;
  deactivate-dry) file=21-deactivate-block135.sql ;;       deactivate) file=21-deactivate-block135.sql; commit=1 ;;
  postcheck) db=mysql; file=99-postcheck.sql ;;
  bundles-check) db=mysql; file=98-bundles-check.sql ;;
  rollback-files-dry) file=90-rollback-file-policy-route.sql ;; rollback-files) file=90-rollback-file-policy-route.sql; commit=1 ;;
  retire-files-dry) file=90b-retire-file-policies.sql ;;   retire-files) file=90b-retire-file-policies.sql; commit=1 ;;
  rollback-seed-dry) file=91-rollback-seed.sql ;;          rollback-seed) file=91-rollback-seed.sql; commit=1 ;;
  rollback-reg-dry) file=92-rollback-registration.sql ;;   rollback-reg) file=92-rollback-registration.sql; commit=1 ;;
  rollback-ddl) file=93-rollback-ddl.sql ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
file="$here/sql/$file"
[[ -f "$file" ]] || { echo "missing $file" >&2; exit 1; }
root_pw="${I18N_REL_MYSQL_PWD:-$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)}"
umask 077
out="$dir/sql-$step-$(date -u +%H%M%SZ).out"
{ cat "$file"; [[ $commit == 1 ]] && printf '\nCOMMIT;\nSELECT "committed" AS result;\n' || true; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" "$container" mysql -uroot --default-character-set=utf8mb4 -t "$db" \
  > "$out" 2>&1 || { cat "$out" >&2; exit 1; }
cat "$out"
