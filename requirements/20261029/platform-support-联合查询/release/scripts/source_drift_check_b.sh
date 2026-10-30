#!/usr/bin/env bash
# 联合查询发布 b 源码漂移检查（只读，可重跑）：发布 worktree Code-MoR-jq-rel 的改动清单与 b 冻结记录相同（241 个文件）、
# 每个文件 SHA-256 不变；main 仍是 6eaf1bd3。研发 worktree Code-MoR-jq-m 已不再使用（§8.7 直接在发布 worktree 修订），不比对。
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; rel=/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel; rc=0
now=$(cd "$rel" && git -c core.quotepath=off status --short -uall | grep -v -E '^\?\? \.jq-notes/' | grep -v node_modules)
if [[ "$now" != "$(cat "$here/evidence/rel-status-b.txt")" ]]; then
  echo "DRIFT: 改动清单变化"; diff <(echo "$now") "$here/evidence/rel-status-b.txt" | head -20; rc=1
fi
(cd "$rel" && shasum -a 256 -c --quiet "$here/evidence/jq-source-files-b.sha256") || { echo "DRIFT: 文件内容变化"; rc=1; }
head=$(git -C "$rel" rev-parse --short=8 main)
[[ "$head" == 6eaf1bd3 ]] || { echo "MAIN-MOVED: main = $head；新提交："; git -C "$rel" log --oneline 6eaf1bd3..main | head -20; rc=1; }
[[ $rc == 0 ]] && echo "all $(wc -l < "$here/evidence/rel-status-b.txt" | tr -d ' ') files unchanged (b); main = 6eaf1bd3"
exit $rc
