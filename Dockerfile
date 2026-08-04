# Stage 1: Build Stage
FROM gradle:jdk25-corretto AS build
ARG GITHUB_REPO_URL=https://github.com/ryeash/full-steam-2
ARG BRANCH=master

# Clone the repository
RUN git clone --depth 1 --branch "${BRANCH}" --single-branch ${GITHUB_REPO_URL} /app
WORKDIR /app

# Build the project with Gradle
RUN ./gradlew clean shadowJar --no-daemon --no-build-cache

# Stage 2: Runtime Stage
FROM amazoncorretto:25-alpine-jdk
EXPOSE 8080

# Create a directory for the application
RUN mkdir /app

# Copy the built JAR from the build stage
COPY --from=build /app/build/libs/*.jar /app/application.jar

# Heap sizing is container-memory-aware: MaxRAMPercentage sets the heap as a
# fraction of the container's memory limit (`docker run --memory=...`), so heap
# and limit stay in sync automatically. Tune the fraction with JAVA_MAX_RAM_PCT
# (default 75). An explicit JAVA_MAX_MEM (e.g. `-e JAVA_MAX_MEM=2g`) still wins,
# overriding the percentage. NB: without a --memory limit, the percentage is of
# the host's RAM — always set a container memory limit in production.
ENV JAVA_MAX_RAM_PCT=75.0

# Run via `sh -c exec` so the shell expands the env vars while `exec` makes java
# replace the shell as PID 1 (keeps SIGTERM working for clean shutdown).
# NOTE: exec form (JSON array) does NOT expand env vars — hence the sh -c.
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseZGC -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+UseStringDeduplication -XX:MaxRAMPercentage=${JAVA_MAX_RAM_PCT:-75.0} ${JAVA_MAX_MEM:+-Xmx}${JAVA_MAX_MEM} -jar /app/application.jar"]