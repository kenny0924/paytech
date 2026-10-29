#!/usr/bin/env bash
# 候选源码漂移核对（runbook ①）：研发 worktree Code-MoR-rq12 的本需求文件与构建候选时（evidence/rq12-source-files.sha256）是否一致、有无新增文件。
# 任一 FAILED / 新增文件 → 候选镜像不含最新改动：停止，重新叠加文件、clean 构建、重建镜像、重写 compose 文件。只读。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
cd /Users/kenny/Desktop/Workspace/Code-MoR-rq12
shasum -a 256 -c "$here/evidence/rq12-source-files.sha256" 2>&1 | grep -v ': OK$' || echo "all $(wc -l < "$here/evidence/rq12-source-files.sha256" | tr -d ' ') files unchanged"
now=$(git -c core.quotepath=off status --short -uall | grep -vE 'node_modules|\.vite-cache|/dist/' | awk '{ $1=""; sub(/^ /,""); print }' | sed 's/^"//; s/"$//' | grep -vE '^requirements/' | sort)
built=$(awk '{ $1=""; sub(/^ +/,""); print }' "$here/evidence/rq12-source-files.sha256" | sort)
added=$(comm -23 <(echo "$now") <(echo "$built"))
[[ -z "$added" ]] && echo "no new files" || { echo "NEW FILES since candidate build:"; echo "$added"; }
git -C /Users/kenny/Desktop/Workspace/Code-MoR log --oneline 6753faba..main | sed 's/^/main moved: /' || true
