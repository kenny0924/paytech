#!/usr/bin/env bash
# 阶段 6 候选树测试（只用 mor-local-*，不跑全 reactor 测试）：三个服务模块 auto 测试 + arch-rules + 全 reactor test-compile。
# 凭据从 local-environment/.env.local 读入环境变量，不打印。日志写 ~/Code-MoR-backups/req02-stage6/logs/test-*.log。
set -uo pipefail
RL=${RL:-/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel}; L=${L:-$HOME/Code-MoR-backups/req02-stage6/logs}
set -a; . /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a
export PAYTECH_REDIS_PASSWORD="$MOR_REDIS_PASSWORD"
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin:$PATH
MVN=(./mvnw -B -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_)
cd "$RL/paytech"
run() { local name=$1; shift; "${MVN[@]}" "$@" > "$L/test-$name.log" 2>&1; echo "$name exit=$?"; }
run channel-connector package -pl channel-connector/paytech-channel-connector-test -am -Dtest='com/paytech/channel/connector/**/*' -Dsurefire.failIfNoSpecifiedTests=false
run client-master package -pl client-master/paytech-client-master-test -am -Dtest='com/paytech/client/**/*' -Dsurefire.failIfNoSpecifiedTests=false
run commercial package -pl commercial/paytech-commercial-test -am -Dtest='com/paytech/commercial/**/*' -Dsurefire.failIfNoSpecifiedTests=false
run arch-rules package -pl tools/arch-rules,tools/arch-rules-reactor-test -am -Dtest='com/paytech/arch/**/*' -Dsurefire.failIfNoSpecifiedTests=false
run reactor-test-compile test-compile
