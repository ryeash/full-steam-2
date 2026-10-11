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
FROM amazoncorretto:25-alpine
EXPOSE 8080

# Create a directory for the application
RUN mkdir /app \
    && addgroup -S appgroup \
    && adduser -S appuser -G appgroup

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1

# Copy the built JAR from the build stage
COPY --from=build /app/build/libs/*.jar /app/application.jar
ENV JAVA_MAX_RAM_PCT=75.0

USER appuser
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseZGC -XX:MaxRAMPercentage=${JAVA_MAX_RAM_PCT:-75.0} ${JAVA_MAX_MEM:+-Xmx}${JAVA_MAX_MEM} -jar /app/application.jar"]