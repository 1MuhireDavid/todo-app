# Multi-stage: the Maven toolchain stays in the build layer and never ships. Both
# stages use Amazon Corretto from Amazon ECR Public, AWS's own OpenJDK build.

FROM public.ecr.aws/docker/library/maven:3.9-amazoncorretto-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so an application-only change does
# not re-download the world on every push.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM public.ecr.aws/docker/library/amazoncorretto:21-alpine AS runtime

# Non-root. Fargate will happily run a container as root; there is no reason to
# let a web process that only needs to read one jar do so.
RUN addgroup -S app && adduser -S -G app -h /app app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/todo-app.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
