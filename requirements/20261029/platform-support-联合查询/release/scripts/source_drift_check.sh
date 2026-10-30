#!/usr/bin/env bash
# 联合查询发布源码漂移检查（只读，可重跑）：
#   1 发布 worktree Code-MoR-jq-rel 的改动清单（git status，排除 .jq-notes / node_modules）与阶段 6 记录相同，236 个文件 SHA-256 不变；
#   2 研发 worktree Code-MoR-jq-m 中非三方合并的 226 个文件与发布 worktree 相同（研发侧在阶段 6 之后又改过即报出）；
#   3 main 仍是 6eaf1bd3（有新提交时列出，需评估是否要并入候选）。
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; rel=/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel; dev=/Users/kenny/Desktop/Workspace/Code-MoR-jq-m
rc=0
now=$(cd "$rel" && git -c core.quotepath=off status --short -uall | grep -v -E '^\?\? \.jq-notes/' | grep -v node_modules)
if [[ "$now" != "$(cat "$here/evidence/rel-status.txt")" ]]; then
  echo "DRIFT: 发布 worktree 改动清单变化"; diff <(echo "$now") "$here/evidence/rel-status.txt" | head -20; rc=1
fi
(cd "$rel" && shasum -a 256 -c --quiet "$here/evidence/jq-source-files.sha256") || { echo "DRIFT: 发布 worktree 文件内容变化"; rc=1; }
merged="operations-console-web/i18n/zh-CN/nav.json operations-console-web/src/i18n/en/nav.json operations-console-web/src/navigation/ConsolePage.tsx
operations-console-web/src/navigation/pageCapabilities.ts operations-console-web/src/configuration/configurationModel.test.ts
paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/resources/application.yml
paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java
paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json
paytech/tools/arch-rules/src/main/resources/table-ownership.yaml paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java"
merged=$(echo $merged)
dev_drift=0
while read -r _ f; do
  case " $merged " in *" $f "*) continue;; esac
  if ! cmp -s "$rel/$f" "$dev/$f"; then echo "DEV-DRIFT: $f"; dev_drift=$((dev_drift+1)); fi
done < <(awk '{print "x", $2}' "$here/evidence/rel-status.txt")
newdev=$(cd "$dev" && git -c core.quotepath=off status --short -uall | grep -v -E '\.jq-notes|node_modules|\.vite-cache' | awk '{print $2}' | sort | comm -23 - <(awk '{print $2}' "$here/evidence/rel-status.txt" | sort))
[[ -n "$newdev" ]] && { echo "DEV-DRIFT: 研发 worktree 新增改动文件："; echo "$newdev" | head -20; dev_drift=$((dev_drift+1)); }
[[ $dev_drift -gt 0 ]] && rc=1
head=$(git -C "$rel" rev-parse --short=8 main)
[[ "$head" == 6eaf1bd3 ]] || { echo "MAIN-MOVED: main = $head；新提交："; git -C "$rel" log --oneline 6eaf1bd3..main | head -20; rc=1; }
[[ $rc == 0 ]] && echo "all 236 files unchanged; dev tree in sync; main = 6eaf1bd3"
exit $rc
