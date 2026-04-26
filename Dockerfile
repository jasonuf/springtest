# Dockerfile — Multi-Stage Build for Spring Boot
# =================================================
# A Dockerfile defines the instructions to build a Docker image.
# Docker images are layered — each instruction (FROM, COPY, RUN) creates
# a new read-only layer. Docker caches layers and only rebuilds changed ones,
# making subsequent builds fast.
#
# MULTI-STAGE BUILD:
# We use two stages to keep the final image small and secure:
#   Stage 1 (builder) — JDK + Maven: compile and package the application
#   Stage 2 (runtime) — JRE only: run the packaged jar
#
# Why? The builder stage includes Maven, the full JDK, source code, and
# all build tools — none of which are needed at runtime. The final image
# only contains the JRE and the fat-jar, reducing:
#   - Image size (JRE vs JDK: ~200MB vs ~400MB)
#   - Attack surface (fewer installed packages = fewer vulnerabilities)
#
# Build the image:
#   docker build -t task-manager:latest .
#
# Run the container:
#   docker run -p 8080:8080 task-manager:latest
#
# Check health:
#   curl http://localhost:8080/actuator/health

# ═══════════════════════════════════════════════════════════════════════════
# STAGE 1: Builder
# ═══════════════════════════════════════════════════════════════════════════
#
# FROM — specifies the base image for this stage.
# maven:3.9-eclipse-temurin-21-alpine:
#   - maven:3.9 — Maven 3.9 (required to build our project)
#   - eclipse-temurin-21 — Adoptium JDK 21 (LTS, production-grade)
#   - alpine — minimal Linux distro (~5MB) for smaller image size
# AS builder — names this stage "builder" so stage 2 can reference it
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# WORKDIR — sets the working directory inside the container.
# All subsequent COPY and RUN commands run relative to this path.
# /app is a common convention for application directories.
WORKDIR /app

# ── Dependency Caching Layer ─────────────────────────────────────────────
# Copy ONLY pom.xml first and download dependencies.
# WHY: Docker caches each layer. If pom.xml hasn't changed between builds,
# Docker reuses the cached dependency layer — skipping the slow download step.
# If we copied all sources first, any code change would invalidate the cache
# and trigger a full dependency re-download.
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode --quiet
# --batch-mode: non-interactive mode (no color, no progress bars) for CI
# --quiet: suppress most output (remove -q to see download progress)
# dependency:go-offline: downloads all declared dependencies

# ── Compile and Package ──────────────────────────────────────────────────
# Now copy the source code and build the fat-jar.
# This layer is invalidated whenever source files change.
COPY src ./src
RUN mvn package --batch-mode --quiet -DskipTests
# -DskipTests: skip tests during Docker build.
# Tests should run in CI BEFORE building the image, not inside the Dockerfile.
# Running tests in Docker adds build time and complicates test reporting.

# ═══════════════════════════════════════════════════════════════════════════
# STAGE 2: Runtime
# ═══════════════════════════════════════════════════════════════════════════
#
# eclipse-temurin:21-jre-alpine — JRE (not JDK, no compiler), Alpine Linux.
# Significantly smaller than the builder image.
FROM eclipse-temurin:17-jre-alpine AS runtime

# ── Security: Non-root User ──────────────────────────────────────────────
# NEVER run containers as root. If the container is compromised, root access
# inside the container could be used to escape to the host.
# Create a dedicated system user and group for running the application.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# addgroup -S: create a system group (no login shell, no home directory)
# adduser -S: create a system user, assigned to appgroup

WORKDIR /app

# ── Copy the fat-jar from the builder stage ──────────────────────────────
# --from=builder: reference the "builder" stage defined with AS builder above.
# /app/target/task-manager-*.jar: glob pattern matches the jar regardless of version.
# We rename it to app.jar for a consistent ENTRYPOINT command.
COPY --from=builder /app/target/task-manager-*.jar app.jar

# Switch to the non-root user before declaring EXPOSE and ENTRYPOINT.
# All processes from here on run as appuser, not root.
USER appuser

# EXPOSE — documents which port the container listens on.
# This is INFORMATIONAL only — it doesn't actually publish the port.
# You must still use -p 8080:8080 (docker run) or ports: (docker-compose)
# to make it accessible from outside the container.
EXPOSE 8080

# ENTRYPOINT — the command that runs when the container starts.
# We use exec form (JSON array) instead of shell form ("java -jar app.jar")
# because exec form runs the process directly (PID 1), not via a shell.
# PID 1 means the JVM receives OS signals (SIGTERM for graceful shutdown) directly.
ENTRYPOINT ["java",
  # UseContainerSupport: JVM respects cgroup memory limits (container's --memory flag).
  # Without this, the JVM reads the HOST machine's RAM and sizes the heap accordingly,
  # potentially allocating more memory than the container is allowed.
  # Enabled by default since JDK 11 — included here for explicitness/documentation.
  "-XX:+UseContainerSupport",
  # MaxRAMPercentage: set heap size to 75% of container's available RAM.
  # Example: container with 512MB RAM → ~384MB heap.
  # Leaves 25% for off-heap (Metaspace, thread stacks, direct buffers, GC overhead).
  "-XX:MaxRAMPercentage=75.0",
  # Graceful shutdown: JVM waits for in-flight requests to complete on SIGTERM.
  # Requires spring.lifecycle.timeout-per-shutdown-phase in application.yml (or default 30s).
  "-Dspring.lifecycle.timeout-per-shutdown-phase=20s",
  "-jar",
  "app.jar"
]

# ── Health Check ──────────────────────────────────────────────────────────
# Docker's built-in health check. Docker marks the container unhealthy if this
# command fails repeatedly. Docker Swarm and some orchestrators use this.
# Kubernetes uses its own readinessProbe/livenessProbe — see k8s/deployment.yaml.
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 -O /dev/null http://localhost:8080/actuator/health \
  || exit 1
# --start-period=40s: don't count failures during first 40s (JVM startup time)
# wget: available in Alpine, unlike curl which isn't pre-installed
