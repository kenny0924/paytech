#!/usr/bin/env bash
# 在指定工作树离线构建本需求涉及的 6 个 exec JAR（不跑测试）。用法：build_apps.sh <工作树> [Maven 仓库]
# 缺省仓库 ~/.m3-refclient/repository；离线时加 _no_tracking_ 规避 "present, but unavailable"（阶段 5 经验）。
set -euo pipefail
tree="${1:?worktree}"; repo="${2:-$HOME/.m3-refclient/repository}"
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin:$PATH
mods=platform-support/paytech-platform-support-app,ledger/paytech-ledger-app,billing/paytech-billing-app,card/paytech-card-app,client-master/paytech-client-master-app,operations-console-bff/paytech-operations-console-bff-app
cd "$tree/paytech"
./mvnw -B -o -q -Dmaven.repo.local="$repo" -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ -DskipTests -pl "$mods" -am clean package
for m in platform-support ledger billing card client-master operations-console-bff; do
  shasum -a 256 "$tree/paytech/$m/paytech-$m-app/target/paytech-$m-app-1.0.0-SNAPSHOT-exec.jar"
done
