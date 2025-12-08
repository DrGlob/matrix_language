# syntax=docker/dockerfile:1

# Build stage
FROM gradle:8.10.2-jdk21-alpine AS builder
WORKDIR /app

# Copy build metadata first to leverage Docker cache for dependencies
COPY gradle ./gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew
RUN ./gradlew --no-daemon --version

# Project sources
COPY src ./src
COPY example.matrix ./example.matrix

# Produce fat JAR with all dependencies (tests are skipped to speed up image build)
RUN ./gradlew clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Application artifact and sample script
COPY --from=builder /app/build/libs/*.jar /app/app.jar
COPY --from=builder /app/example.matrix /app/example.matrix

# Default entrypoint: starts REPL when no args are passed; accepts optional path to a script file
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD []
