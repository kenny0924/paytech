#!/usr/bin/env bash
# 候选树漂移检查（只读，可重跑）：main 仍为 9eab368c；候选树 Code-MoR-po-rel 的 HEAD 为 9eab368c，
# 工作区改动（M + ??，排除构建产物）恰好是 evidence/source-files.sha256 的文件集，且逐文件哈希一致；
# 已跟踪文件中没有清单外的差异（git diff 9eab368c 只列清单内文件）。不一致即退出 1。
set -euo pipefail; export LC_ALL=C
here="$(cd "$(dirname "$0")/.." && pwd)"; RL=/Users/kenny/Desktop/Workspace/Code-MoR-po-rel; BASE=9eab368c
main=$(git -C /Users/kenny/Desktop/Workspace/Code-MoR rev-parse --short=8 main); head=$(git -C "$RL" rev-parse --short=8 HEAD)
echo "main=$main candidate_head=$head expected=$BASE"
[ "$main" = "$BASE" ] || { echo "DRIFT: main moved (git log $BASE..main)"; exit 1; }
[ "$head" = "$BASE" ] || { echo "DRIFT: candidate HEAD moved"; exit 1; }
cd "$RL"
git status --short -uall | grep -vE '(^| )(.*/)?(node_modules|target|dist|i18n-out|\.vite-cache)/' | awk '{print $2}' | LC_ALL=C sort > /tmp/w8-drift-status.$$
awk '{print $2}' "$here/evidence/source-files.sha256" | LC_ALL=C sort > /tmp/w8-drift-list.$$
git diff --name-only "$BASE" | LC_ALL=C sort > /tmp/w8-drift-diff.$$
trap 'rm -f /tmp/w8-drift-*.$$' EXIT
extra=$(comm -23 /tmp/w8-drift-status.$$ /tmp/w8-drift-list.$$ | wc -l | tr -d ' '); missing=$(comm -13 /tmp/w8-drift-status.$$ /tmp/w8-drift-list.$$ | wc -l | tr -d ' ')
diffextra=$(comm -23 /tmp/w8-drift-diff.$$ /tmp/w8-drift-list.$$ | wc -l | tr -d ' ')
hashbad=$(shasum -a 256 -c "$here/evidence/source-files.sha256" 2>/dev/null | grep -vc ': OK$' || true)
echo "files=$(wc -l < /tmp/w8-drift-list.$$ | tr -d ' ') status_extra=$extra status_missing=$missing tracked_diff_outside_list=$diffextra hash_mismatch=$hashbad"
comm -23 /tmp/w8-drift-status.$$ /tmp/w8-drift-list.$$ | sed 's/^/  extra: /'; comm -13 /tmp/w8-drift-status.$$ /tmp/w8-drift-list.$$ | sed 's/^/  missing: /'
[ "$extra$missing$diffextra$hashbad" = "0000" ] && echo "NO DRIFT" || { echo "DRIFT"; exit 1; }
