#!/usr/bin/env bash
# 候选源码漂移核对（runbook ①，只读）。候选基线 = main 022da95a（含联合查询提交 d673817f 与多语言发布变更记录提交 1c8341a2）。
#   1) 研发 worktree Code-MoR-rq04 的本需求文件与构建候选时（evidence/rq04-source-files.sha256）是否一致、有无新增文件；
#   2) 发布 worktree Code-MoR-rq04-rel 相对 022da95a 的代码差异只能是本需求文件（requirements / agent-docs / design-discuss 等文档除外）；
#   3) main 是否前进到 022da95a 之后。
# 任一 FAILED / 新增文件 / 意外差异 → 候选镜像不含最新改动或混入他人改动：停止，重新叠加、clean 构建、重建镜像、重写 compose 文件。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; BASE=022da95a
list="$here/evidence/rq04-source-files.sha256"
cd /Users/kenny/Desktop/Workspace/Code-MoR-rq04
shasum -a 256 -c "$list" 2>&1 | grep -v ': OK$' || echo "rq04: all $(wc -l < "$list" | tr -d ' ') files unchanged"
now=$(git -c core.quotepath=off status --short -uall | grep -vE 'node_modules|\.vite-cache|/dist/' | awk '{ $1=""; sub(/^ /,""); print }' | grep -vE '^requirements/' | sort)
built=$(awk '{ $1=""; sub(/^ +/,""); print }' "$list" | sort)
added=$(comm -23 <(echo "$now") <(echo "$built"))
[[ -z "$added" ]] && echo "rq04: no new files" || { echo "rq04: NEW FILES since candidate build:"; echo "$added"; }
cd /Users/kenny/Desktop/Workspace/Code-MoR-rq04-rel
unexpected=$(git -c core.quotepath=off diff --name-only "$BASE" | grep -vE '^(requirements|agent-docs|design-discuss)/|^AGENTS\.md$' | sort | comm -23 - <(echo "$built"))
[[ -z "$unexpected" ]] && echo "release tree: code = $BASE + REQ-04 only" || { echo "release tree: UNEXPECTED code differences vs $BASE:"; echo "$unexpected"; }
git -C /Users/kenny/Desktop/Workspace/Code-MoR log --oneline "$BASE"..main | sed 's/^/main moved: /' || true
