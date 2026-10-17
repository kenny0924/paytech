# Code-MoR local Flink + Flink CDC image (analytics profile). Used by flink-jobmanager, flink-taskmanager and
# flink-cdc-submit. Every downloaded artifact is verified with SHA-256 at build time.
#
# Base image : flink:1.20.5-scala_2.12-java17 (Flink 1.20.5, released 2026-06-08), linux/arm64 + amd64
#              sha256:80401a8ee20e2246718c01303712c10ccaf13961e31316d9b8c4549f7b2cfc9b
# Flink CDC  : 3.7.0 for Flink 1.20 (released 2026-09-29)
#   flink-cdc-3.7.0-1.20-bin.tar.gz                    c7c48f9011e2835ceebaf25d98074e44716a106248da285d0a723fa738385a78
#   flink-cdc-pipeline-connector-mysql-3.7.0-1.20.jar  59ed5bbf92f6e8b7d2291d6365a2d0b12438fb011892d8898b756f1d718339f9
#   flink-cdc-pipeline-connector-doris-3.7.0-1.20.jar  72537db0a9b832732f756b0c846dcdc1e83a24d23671e3a126181e4c6be6e626
# JDBC driver: mysql:mysql-connector-java:8.0.27 (version named by the Flink CDC MySQL connector documentation)
#   mysql-connector-java-8.0.27.jar                    d670e85fa1075a80d91b8f17dbd620d76717bc20c74ab4aea8356e37a8545ffe
# The SHA-256 values above were computed after checking each file against the upstream .sha512 (Apache dist)
# or .sha1 (Maven Central) published next to it.
FROM flink@sha256:80401a8ee20e2246718c01303712c10ccaf13961e31316d9b8c4549f7b2cfc9b

ARG FLINK_CDC_VERSION=3.7.0
ARG FLINK_CDC_FLINK_LINE=1.20
ARG MAVEN_CENTRAL=https://repo1.maven.org/maven2
ARG APACHE_ARCHIVE=https://archive.apache.org/dist/flink

RUN set -eux; \
    cdc="${FLINK_CDC_VERSION}-${FLINK_CDC_FLINK_LINE}"; \
    work="$(mktemp -d)"; cd "$work"; \
    curl -fsSLo flink-cdc.tar.gz "${APACHE_ARCHIVE}/flink-cdc-${FLINK_CDC_VERSION}/flink-cdc-${cdc}-bin.tar.gz"; \
    curl -fsSLo connector-mysql.jar "${MAVEN_CENTRAL}/org/apache/flink/flink-cdc-pipeline-connector-mysql/${cdc}/flink-cdc-pipeline-connector-mysql-${cdc}.jar"; \
    curl -fsSLo connector-doris.jar "${MAVEN_CENTRAL}/org/apache/flink/flink-cdc-pipeline-connector-doris/${cdc}/flink-cdc-pipeline-connector-doris-${cdc}.jar"; \
    curl -fsSLo mysql-connector-java.jar "${MAVEN_CENTRAL}/mysql/mysql-connector-java/8.0.27/mysql-connector-java-8.0.27.jar"; \
    printf '%s\n' \
      "c7c48f9011e2835ceebaf25d98074e44716a106248da285d0a723fa738385a78  flink-cdc.tar.gz" \
      "59ed5bbf92f6e8b7d2291d6365a2d0b12438fb011892d8898b756f1d718339f9  connector-mysql.jar" \
      "72537db0a9b832732f756b0c846dcdc1e83a24d23671e3a126181e4c6be6e626  connector-doris.jar" \
      "d670e85fa1075a80d91b8f17dbd620d76717bc20c74ab4aea8356e37a8545ffe  mysql-connector-java.jar" \
      | sha256sum -c -; \
    mkdir -p /opt/flink-cdc; \
    tar -xzf flink-cdc.tar.gz -C /opt/flink-cdc --strip-components=1; \
    mv connector-mysql.jar "/opt/flink-cdc/lib/flink-cdc-pipeline-connector-mysql-${cdc}.jar"; \
    mv connector-doris.jar "/opt/flink-cdc/lib/flink-cdc-pipeline-connector-doris-${cdc}.jar"; \
    mv mysql-connector-java.jar /opt/flink/lib/mysql-connector-java-8.0.27.jar; \
    chown -R flink:flink /opt/flink-cdc /opt/flink/lib/mysql-connector-java-8.0.27.jar; \
    cd /; rm -rf "$work"

ENV FLINK_CDC_HOME=/opt/flink-cdc
