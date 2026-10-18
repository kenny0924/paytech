#!/bin/zsh
set -euo pipefail
# 从 paytech/ 单 Reactor 构建 acquiring 测试类路径后启动本地 Customer Checkout Mock（第 03 步路径改写；入口类沿用原值）。
cd "${0:A:h:h:h}"
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw -q -pl acquiring/paytech-acquiring-test -am package -DskipTests dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=target/customer-mock-classpath.txt
# Reactor JARs are already in the generated classpath; adding their classes again duplicates Mappers.
test_module=acquiring/paytech-acquiring-test
acquiring_mock_classpath="$test_module/target/test-classes:$test_module/target/classes:$(cat $test_module/target/customer-mock-classpath.txt)"
exec "$JAVA_HOME/bin/java" -cp "$acquiring_mock_classpath" \
  com.paytech.acquiring.manual.customer.CustomerCheckoutMockApplication "$@"
