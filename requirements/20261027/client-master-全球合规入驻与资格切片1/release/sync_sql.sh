#!/usr/bin/env bash
# 把本需求 db/ 下由基础设施维护的 SQL 按执行顺序复制到 release/sql/，并重写 SHA256SUMS。
# 源文件改动（如块 138 CLIENT 补充件生成）后重跑；release/sql 下的文件不要手改。
# client-master（20—23）与 compliance（30—32）变更 SQL 由本脚本从 paytech/ 复制；源文件改动后重跑。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; db="$here/../db"; out="$here/sql"; mkdir -p "$out"
copy() { cp "$db/$1" "$out/$2"; }
copy vault/dml-check.sql                         00-vault-precheck.sql
copy platform-support/block138-precheck.sql      01-platform-block138-precheck.sql
copy vault/dml.sql                               10-vault-legal-identifier.sql
copy vault/dml-verify.sql                        11-vault-verify.sql
# client-master（cm-sql 定稿，2026-10-27）：顺序 20 → 21 → 22 → 23 → 29
cmc="$here/../../../../paytech/client-master/docs/database/sql/changes"
cp "$cmc/2026-10-27-onboarding-global.sql"                 "$out/20-client-master-ddl.sql"
cp "$cmc/2026-10-27-onboarding-global-cleanup.sql"         "$out/21-client-master-cleanup.sql"
cp "$cmc/2026-10-27-onboarding-country-pack.sql"           "$out/22-client-master-country-pack.sql"
cp "$cmc/2026-10-27-onboarding-global-legacy-backfill.sql" "$out/23-client-master-legacy-backfill.sql"
copy client-master/legacy-redaction-verify.sql   29-client-master-legacy-redaction-verify.sql
# compliance（cmp-dev 定稿，2026-10-27）：顺序 30 → 31（同会话先 SET @approved_by）→ 32
cmp="$here/../../../../paytech/compliance/docs/database/sql"
cp "$cmp/changes/2026-10-27-onboarding-global.sql"          "$out/30-compliance-ddl.sql"
cp "$cmp/compliance-initial-data.sql"                       "$out/31-compliance-initial-data.sql"
cp "$cmp/changes/2026-10-27-onboarding-global-cleanup.sql"  "$out/32-compliance-cleanup.sql"
copy platform-support/registration-block138.sql  60-platform-block138-registration.sql
copy platform-support/block138-activate.sql      61-platform-block138-activate.sql
copy platform-support/block138-deactivate.sql    62-platform-block138-deactivate.sql
copy platform-support/block138-rollback.sql      69-platform-block138-rollback.sql
copy vault/dml-rollback.sql                      89-vault-rollback.sql
if [[ -f "$db/platform-support/registration-block138-client.sql" ]]; then
  copy platform-support/block138-client-precheck.sql      02-platform-block138-client-precheck.sql
  copy platform-support/registration-block138-client.sql  63-platform-block138-client-registration.sql
  copy platform-support/block138-client-activate.sql      64-platform-block138-client-activate.sql
  copy platform-support/block138-client-deactivate.sql    65-platform-block138-client-deactivate.sql
  copy platform-support/block138-client-rollback.sql      68-platform-block138-client-rollback.sql
fi
(cd "$out" && shasum -a 256 [0-9]*.sql > SHA256SUMS)
ls "$out"
