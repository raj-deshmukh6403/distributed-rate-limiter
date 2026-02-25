# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Use the full JDK + Maven image just for compiling
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first
# Docker caches this layer — dependencies only re-download when pom.xml changes
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Use slim JRE only — no compiler, no Maven, much smaller image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the built jar from stage 1
COPY --from=builder /app/target/*.jar app.jar

# Expose the port Spring Boot runs on
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]