FROM eclipse-temurin:25.0.4_7-jre-noble@sha256:b4c93a50fc67612798db73d68ca3b0ee4ebdd51736e59cca370e689b9797037e
ARG JAR_FILE
WORKDIR /app
COPY --chown=10001:10001 ${JAR_FILE} /app/application.jar
COPY --chown=10001:10001 health-probe/ /app/health-probe/
USER 10001:10001
ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-Duser.home=/tmp", "-Djava.io.tmpdir=/tmp", "-jar", "/app/application.jar"]
