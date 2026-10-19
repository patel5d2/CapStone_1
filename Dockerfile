# Every base image is pinned by digest, so rebuilding a commit pulls the same bytes.
# Dependabot (docker ecosystem) bumps the tag and the digest together; nothing in this
# file upgrades packages at build time, which would make two builds of one commit differ.

# ============================================================
# Stage 1a: Frontend build (React + TypeScript SPA)
# ============================================================
# Node 22 everywhere: frontend/.node-version drives CI and Cloudflare to the same major.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS frontend
WORKDIR /frontend

# Install dependencies first so this layer caches on lockfile changes only
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# The Clerk publishable key is baked in at build time. Publishable keys are
# meant to be public; override per environment with --build-arg.
ARG VITE_CLERK_PUBLISHABLE_KEY=pk_test_aW5ub2NlbnQtbWFjYXctMTY4Ny5jbGVyay5hY2NvdW50cy5kZXYk
ENV VITE_CLERK_PUBLISHABLE_KEY=$VITE_CLERK_PUBLISHABLE_KEY
ARG VITE_REQUIRE_TWO_FACTOR=true
ENV VITE_REQUIRE_TWO_FACTOR=$VITE_REQUIRE_TWO_FACTOR
# vite.config.ts writes the bundle to ../src/main/resources/static, which
# resolves to /src/main/resources/static inside this stage.
RUN npm run build

# ============================================================
# Stage 1b: Build
# ============================================================
FROM eclipse-temurin:21-jdk-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS build
WORKDIR /app

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
# The application JAR. A local `docker build` / `docker compose up --build` compiles it
# from source above. CI replaces this whole stage with the JAR it already tested
# (`--build-context app-jar=<dir holding app.jar>`), so the stages above never run there
# and the image, the GitHub release and the VM deploy all ship that one artifact.
# ============================================================
FROM scratch AS app-jar
COPY --from=build /app/target/*.jar /app.jar

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555 AS runtime
WORKDIR /app

# Version, revision and source labels are added by docker/metadata-action at release.
LABEL maintainer="dharminpatel,jonathansoriano,matthewbrown,shamakpatel,jessicapham" \
    description="EnterpriseDevGroupProject Spring Boot Application"

# Create a non-root group and user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=app-jar --chown=appuser:appgroup /app.jar app.jar

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
