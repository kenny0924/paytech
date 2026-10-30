#!/usr/bin/env bash
# 候选树漂移检查（只读，可重跑；git 一律 --no-optional-locks，不写任何 worktree 的 index）：
# ① main 仍为 331d8e0e；② 候选树 Code-MoR-req02-rel 的 HEAD 为 331d8e0e，工作区改动（M + ??，排除构建产物）恰好是 evidence/source-files.sha256
#    的 87 个文件且逐文件哈希一致，已跟踪文件没有清单外差异；③ 研发树 Code-MoR-req02 的 69 个文件仍与候选树相同（研发树有新改动即 DRIFT）；
# ④ 明细账运维发布树 Code-MoR-plops-rel 的 18 个 ops-web 文件仍与候选树相同；⑤ mor-test 四个容器镜像 = baseline.md。任一不符输出 DRIFT 并退出 1。
set -uo pipefail; export LC_ALL=C
here="$(cd "$(dirname "$0")/.." && pwd)"; RL=/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel; BASE=331d8e0e; bad=0; t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
main=$(git -C /Users/kenny/Desktop/Workspace/Code-MoR rev-parse --short=8 main); head=$(git -C "$RL" rev-parse --short=8 HEAD)
echo "main=$main candidate_head=$head expected=$BASE"
[ "$main" = "$BASE" ] || { echo "DRIFT: main moved (git log $BASE..main)"; bad=1; }
[ "$head" = "$BASE" ] || { echo "DRIFT: candidate HEAD moved"; bad=1; }
git -C "$RL" --no-optional-locks status --short -uall | grep -vE '(^| )(.*/)?(node_modules|target|dist|i18n-out|\.vite-cache)/' | awk '{print $2}' | sort > "$t/status"
awk '{print $2}' "$here/evidence/source-files.sha256" | sort > "$t/list"
git -C "$RL" --no-optional-locks diff --name-only "$BASE" | sort > "$t/diff"
extra=$(comm -23 "$t/status" "$t/list" | wc -l | tr -d ' '); missing=$(comm -13 "$t/status" "$t/list" | wc -l | tr -d ' ')
diffextra=$(comm -23 "$t/diff" "$t/list" | wc -l | tr -d ' ')
hashbad=$(cd "$RL" && shasum -a 256 -c "$here/evidence/source-files.sha256" 2>/dev/null | grep -vc ': OK$' || true)
echo "candidate files=$(wc -l < "$t/list" | tr -d ' ') status_extra=$extra status_missing=$missing tracked_diff_outside_list=$diffextra hash_mismatch=$hashbad"
comm -23 "$t/status" "$t/list" | sed 's/^/  extra: /'; comm -13 "$t/status" "$t/list" | sed 's/^/  missing: /'
[ "$extra$missing$diffextra$hashbad" = "0000" ] || bad=1
dev=$(cd /Users/kenny/Desktop/Workspace/Code-MoR-req02 && shasum -a 256 -c "$here/evidence/source-files-req02.sha256" 2>/dev/null | grep -vc ': OK$' || true)
devn=$(git -C /Users/kenny/Desktop/Workspace/Code-MoR-req02 --no-optional-locks status --short -uall | grep -vE '(^| )(.*/)?(node_modules|target|dist|i18n-out|\.vite-cache)/' | wc -l | tr -d ' ')
echo "dev tree Code-MoR-req02: changed_files=$devn (expected 69) hash_mismatch=$dev"; [ "$dev" = 0 ] && [ "$devn" = 69 ] || { echo "DRIFT: dev tree changed"; bad=1; }
pl=$(cd /Users/kenny/Desktop/Workspace/Code-MoR-plops-rel && shasum -a 256 -c "$here/evidence/source-files-plops-opsweb.sha256" 2>/dev/null | grep -vc ': OK$' || true)
echo "plops release tree ops-web 18 files: hash_mismatch=$pl"; [ "$pl" = 0 ] || { echo "DRIFT: plops ops-web files changed"; bad=1; }
while read -r svc id; do
  live=$(docker inspect "mor-test-$svc-1" --format '{{.Image}}' 2>/dev/null)
  [ "$live" = "$id" ] && echo "live $svc ok" || { echo "DRIFT live $svc=$live expected $id"; bad=1; }
done <<'IDS'
channel-connector-service sha256:23679d451fe44dfce08426777b5ec7cbc73a60253c604f149dfc48291c3890aa
client-master-service sha256:2b12c2e2184bb9e926c5fb6a74822c103ba5a659a53dcbacb9a4d6bb60c414bc
commercial-service sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728
operations-console-web sha256:5784fa817c471ebce707821dfd4db0727dd28cbe073776e620fb48a9f5d800b4
IDS
[ "$bad" = 0 ] && echo "NO DRIFT" || { echo "DRIFT"; exit 1; }
