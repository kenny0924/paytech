#!/usr/bin/env bash
# mor-test 基线核对（只读，可重跑）：读 6 个运行容器的镜像 ID 与覆盖来源，拷出 /app/application.jar（只读方向），
# 与 <基线工作树> 已构建的 exec JAR 按条目 CRC 比较（jarcmp.py：递归展开 BOOT-INF/lib/paytech-*.jar，忽略 MANIFEST / idx / pom.properties）。
# 用法：baseline_check.sh <已用 build_apps.sh 构建的基线工作树> <输出目录>
# 输出：<输出目录>/images.tsv、live/<服务>.jar、compare-<服务>.txt；终端打印每个服务的结论。
# 分类：compare 中"现网独有（-）/ 两边不同（~）"的条目按 scripts/expected-diffs.tsv（ALLOW 环境变量可换文件）分为已知 / 未知；
#   未知 > 0 即现网有未登记的改动，停下人工判断（不要当作可发布）。基线工作树换成含 card-BIN 提交的 main 后，已知差异应清零。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; base="${1:?base worktree}"; out="${2:?out dir}"; mkdir -p "$out/live"
: > "$out/images.tsv"
for pair in platform-support-service:platform-support ledger-service:ledger billing-service:billing card-service:card \
            client-master-service:client-master operations-console-bff:operations-console-bff; do
  svc="${pair%%:*}"; mod="${pair#*:}"; c="mor-test-$svc-1"
  img="$(docker inspect "$c" --format '{{.Image}}')"; created="$(docker inspect "$c" --format '{{.Created}}')"
  src="$(docker inspect "$c" --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}' | tr ',' '\n' | tail -n +4 | tr '\n' ' ')"
  printf '%s\t%s\t%s\t%s\n' "$svc" "$img" "$created" "$src" >> "$out/images.tsv"
  docker cp -q "$c:/app/application.jar" "$out/live/$svc.jar"
  python3 -I "$here/jarcmp.py" "$out/live/$svc.jar" "$base/paytech/$mod/paytech-$mod-app/target/paytech-$mod-app-1.0.0-SNAPSHOT-exec.jar" 400 > "$out/compare-$svc.txt"
  verdict="$(python3 -I - "$out/compare-$svc.txt" "$svc" "${ALLOW:-$here/expected-diffs.tsv}" <<'PY'
import sys, re
compare, svc, allow = sys.argv[1:]
known = {}
for line in open(allow, encoding="utf-8"):
    if line.startswith("#") or not line.strip():
        continue
    s, entry, reason = line.rstrip("\n").split("\t")
    if s == svc:
        known[entry] = reason
k = u = 0; reasons = set(); unknown = []
for line in open(compare).read().splitlines()[1:]:
    m = re.match(r"^  ([-~]) (.*)$", line)
    if not m:
        continue
    if m.group(2) in known:
        k += 1; reasons.add(known[m.group(2)])
    else:
        u += 1; unknown.append(m.group(1) + " " + m.group(2))
print(f"已知差异 {k}{'（' + '；'.join(sorted(reasons)) + '）' if reasons else ''}，未知差异 {u}" + ("  ← 停：" + "; ".join(unknown[:5]) if u else ""))
PY
)"
  printf '%-26s %s  live-jar %s  %s | %s\n' "$svc" "${img:7:12}" "$(shasum -a 256 "$out/live/$svc.jar" | cut -c1-12)" "$(head -1 "$out/compare-$svc.txt")" "$verdict"
done
