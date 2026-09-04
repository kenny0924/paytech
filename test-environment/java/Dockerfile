FROM eclipse-temurin:25.0.4_7-jre-noble@sha256:b4c93a50fc67612798db73d68ca3b0ee4ebdd51736e59cca370e689b9797037e
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
ARG JAR_FILE
WORKDIR /app
COPY --chown=10001:10001 ${JAR_FILE} /app/application.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -Duser.home=/tmp -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
