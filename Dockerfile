# ============================================================
# Stage 1a: Frontend build (React + TypeScript SPA)
# ============================================================
FROM node:22-alpine AS frontend
WORKDIR /frontend

# Install dependencies first so this layer caches on lockfile changes only
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# The Clerk publishable key is baked in at build time. Publishable keys are
# meant to be public; override per environment with --build-arg.
ARG VITE_CLERK_PUBLISHABLE_KEY=pk_test_bWlnaHR5LWVzY2FyZ290LTY1NjIuY2xlcmsuYWNjb3VudHMuZGV2JA
ENV VITE_CLERK_PUBLISHABLE_KEY=$VITE_CLERK_PUBLISHABLE_KEY
ARG VITE_REQUIRE_TWO_FACTOR=false
ENV VITE_REQUIRE_TWO_FACTOR=$VITE_REQUIRE_TWO_FACTOR
# vite.config.ts writes the bundle to ../src/main/resources/static, which
# resolves to /src/main/resources/static inside this stage.
RUN npm run build

# ============================================================
# Stage 1b: Build
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Fix vulnerabilities by updating Alpine packages
RUN apk update && apk upgrade --no-cache

# Add metadata labels
LABEL maintainer="dharminpatel,jonathansoriano,matthewbrown,shamakpatel,jessicapham" \
    version="0.1.1" \
    description="EnterpriseDevGroupProject Spring Boot Application"

# Copy the Maven wrapper and pom.xml first to leverage Docker layer caching
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies (BuildKit cache mount for Maven repo)
RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw dependency:go-offline -q

# Copy source, drop the compiled SPA into the static resources the JAR serves,
# then build the application JAR
COPY src ./src
COPY --from=frontend /src/main/resources/static ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw clean package -DskipTests -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Fix vulnerabilities
RUN apk update && apk upgrade --no-cache

# Create a non-root group and user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the compiled JAR from the build stage
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Switch to the non-root user
USER appuser

# Expose the default Spring Boot port
EXPOSE 8080

# Health check: ping the app every 30s; fail after 3 consecutive failures
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Allow JVM tuning via JAVA_OPTS at runtime (e.g., -e JAVA_OPTS="-Xmx512m")
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
