#!/bin/zsh
set -euo pipefail
cd "${0:A:h:h}"
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw -q -pl paytech-commerce-test -am package -DskipTests dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=target/customer-mock-classpath.txt
# Reactor JARs are already in the generated classpath; adding their classes again duplicates Mappers.
commerce_mock_classpath="paytech-commerce-test/target/test-classes:paytech-commerce-test/target/classes:$(cat paytech-commerce-test/target/customer-mock-classpath.txt)"
exec "$JAVA_HOME/bin/java" -cp "$commerce_mock_classpath" \
  com.paytech.acquiring.manual.customer.CustomerCheckoutMockApplication "$@"
