#!/usr/bin/env bash
# 把本需求的发布 SQL 按执行顺序复制到 release/sql/，并重写 SHA256SUMS。源文件改动后重跑；release/sql 下的文件不要手改。
# 源：SRC（默认发布构建快照 Code-MoR-refdata-rel，镜像 refdata-20261027a 由它构建）中的服务 changes/；块 139 来自本需求 db/platform-support/；
#   release-owned-sql/ 为发布专用的只读核对与注释回退。另与 worktree Code-MoR-refdata 对比，不一致时报警（说明代码在冻结后又改了）。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; db="$here/../db/platform-support"; own="$here/release-owned-sql"; out="$here/sql"; mkdir -p "$out"
SRC="${SRC:-/Users/kenny/Desktop/Workspace/Code-MoR-refdata-rel}"; WT="${WT:-/Users/kenny/Desktop/Workspace/Code-MoR-refdata}"
pfc="paytech/platform-support/docs/database/sql/changes"; cmc="paytech/client-master/docs/database/sql/changes"
rm -f "$out"/[0-9]*.sql
src() { if [[ -f "$WT/$1" ]] && ! cmp -s "$SRC/$1" "$WT/$1"; then echo "WARN: $1 differs between $SRC and $WT" >&2; fi; cp "$SRC/$1" "$out/$2"; }
cp "$own/../precheck/release-precheck-platform.sql"      "$out/00-platform-release-precheck.sql"
cp "$db/block139-precheck.sql"                            "$out/01-platform-block139-precheck.sql"
cp "$own/../precheck/release-precheck-client-master.sql" "$out/02-client-master-release-precheck.sql"
src "$pfc/2026-10-27-reference-data.sql"                       10-platform-reference-data.sql
cp "$own/platform-reference-data-verify.sql"              "$out/11-platform-reference-data-verify.sql"
src "$pfc/2026-10-27-reference-data-subdivision-local-name.sql" 12-platform-subdivision-local-name.optional.sql
src "$cmc/2026-10-27-reference-data-catalog.sql"               20-client-master-reference-data-catalog.sql
src "$cmc/2026-10-27-reference-data-catalog-comment.sql"       21-client-master-reference-data-catalog-comment.sql
cp "$db/registration-block139.sql"                        "$out/60-platform-block139-registration.sql"
cp "$db/block139-activate.sql"                            "$out/61-platform-block139-activate.sql"
cp "$db/block139-deactivate.sql"                          "$out/62-platform-block139-deactivate.sql"
cp "$db/block139-rollback.sql"                            "$out/69-platform-block139-rollback.sql"
src "$cmc/2026-10-27-reference-data-catalog-rollback.sql"      80-client-master-reference-data-catalog-rollback.sql
cp "$own/client-master-comment-rollback.sql"              "$out/81-client-master-comment-rollback.optional.sql"
src "$pfc/2026-10-27-reference-data-subdivision-local-name-rollback.sql" 88-platform-subdivision-local-name-rollback.optional.sql
src "$pfc/2026-10-27-reference-data-rollback.sql"              89-platform-reference-data-rollback.sql
(cd "$out" && shasum -a 256 [0-9]*.sql > SHA256SUMS)
ls "$out"
