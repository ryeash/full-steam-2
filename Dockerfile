# Stage 1: Build Stage
FROM gradle:jdk21-corretto AS build
ARG GITHUB_REPO_URL=https://github.com/ryeash/full-steam
ARG BRANCH=master

# Clone the repository
RUN git clone --depth 1 --branch "${BRANCH}" --single-branch ${GITHUB_REPO_URL} /app
WORKDIR /app

# Build the project with Gradle
RUN ./gradlew clean shadowJar --no-daemon --no-build-cache

# Stage 2: Runtime Stage
FROM amazoncorretto:21-alpine-jdk
EXPOSE 8080

# Create a directory for the application
RUN mkdir /app

# Copy the built JAR from the build stage
COPY --from=build /app/build/libs/*.jar /app/application.jar

# Define the entry point to run the application
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+UseCompressedOops", "-XX:+UseCompressedClassPointers", "-XX:+UseStringDeduplication", "-Xmx1g", "-jar", "/app/application.jar"]