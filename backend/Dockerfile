# Stage 1 — build
FROM gradle:8.13-jdk21 AS builder

WORKDIR /workspace

# Copy only build scripts to cache the dependency download layer.
# This layer is invalidated only when build.gradle.kts changes, not on source edits.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts ./
COPY backend/build.gradle.kts backend/settings.gradle.kts backend/

# Pre-fetch all Gradle dependencies before source arrives.
# Stub out the source directory so the :dependencies task resolves without src.
RUN mkdir -p backend/src/main/kotlin && \
    gradle :backend:dependencies --no-daemon

# Source layer — invalidated on every src change, but deps are already cached above.
COPY backend/src/ backend/src/
RUN gradle :backend:bootJar --no-daemon -x test

# Stage 2 — runtime
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

COPY --from=builder /workspace/backend/build/libs/*.jar app.jar

EXPOSE 8080

# JVM flags tuned for Render.com free tier (512 MB cgroup limit).
#
# MaxRAMPercentage=45.0  → 512*0.45 = 230 MB heap max (reduced from 55%/281 MB).
#   Observed peak heap usage is ~145 MB, so 85 MB headroom remains.
#   G1 at idle commits ~130-140 MB (MinHeapFreeRatio=10 aggressively shrinks).
#
# InitialRAMPercentage=10.0  → start with a small committed heap (~51 MB) and
#   let G1 expand on demand; reduces RSS spike during startup warmup.
#
# MaxMetaspaceSize=192m  → increased from 128m. App grew: 11 JPA entities +
#   36 Spring beans + Hibernate bytecode + Jackson dynamic classes pushed
#   Metaspace above the old 128m cap, causing OOM crashes on 2026-04-29.
#   192m gives ~50 MB headroom over observed ~140 MB usage.
#
# MinHeapFreeRatio=10 / MaxHeapFreeRatio=20  → after each major GC, if more
#   than 20% of the heap is free G1 shrinks committed heap back down.
#   Keeps idle committed heap ~130-140 MB (not 230 MB max).
#
# G1PeriodicGCInterval=60000  → trigger a periodic GC every 60 s when the
#   JVM is idle (no traffic). Fires the heap-shrink logic above even
#   without incoming requests, keeping idle RSS low on Render's free tier.
#
# ExitOnOutOfMemoryError  → crash fast on any OOM rather than thrashing.
#   Render restarts the container within seconds, which is far better than
#   a hung JVM that can no longer serve requests.
#
# Budget: heap(230) + metaspace(192) + CodeCache(35) + CCS(14) + overhead(20)
#   = 491 MB reserved max. Idle RSS ≈ 350-380 MB → ~130-160 MB headroom.
#
# Note: no HeapDumpPath — Render has no persistent FS; use GET /actuator/heapdump instead.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=45.0", \
  "-XX:InitialRAMPercentage=10.0", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:MaxMetaspaceSize=192m", \
  "-XX:MinHeapFreeRatio=10", \
  "-XX:MaxHeapFreeRatio=20", \
  "-XX:G1PeriodicGCInterval=60000", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
