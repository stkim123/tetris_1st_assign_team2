FROM eclipse-temurin:8-jdk-jammy AS build
# Wrapper checksum pins the ZIP; unzip prevents its tar.gz fallback on Linux.
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip fontconfig fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
ENV MAVEN_OPTS="-Xmx256m"
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
# Java 8 compile plus every existing main/-ea headless suite.
RUN ./mvnw -B -ntp verify

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/tetris-server.jar /app/tetris-server.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Xms64m", "-Xmx256m", "-XX:MaxDirectMemorySize=96m", "-jar", "/app/tetris-server.jar"]
