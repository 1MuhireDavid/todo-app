# Multi-stage: the Maven toolchain and the full JDK stay in the build layer and
# never ship. The runtime layer is a JRE plus one jar.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so an application-only change does
# not re-download the world on every push.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root. Fargate will happily run a container as root; there is no reason to
# let a web process that only needs to read one jar do so.
RUN addgroup -S app && adduser -S -G app -h /app app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/todo-app.jar /app/app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes the heap from
# whatever the task definition actually granted, so changing TaskMemory in
# CloudFormation does not require rebuilding the image.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
