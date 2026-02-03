# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and related files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source code and resources
COPY src src

# Make gradlew executable
RUN chmod +x gradlew

# Build the application
# Use --no-daemon to avoid issues in containerized environments
RUN ./gradlew bootJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Set arguments for Spring Boot application
ARG JAR_FILE=build/libs/*.jar

# Copy the JAR file from the builder stage
COPY --from=builder /app/${JAR_FILE} app.jar

# Expose the port the Spring Boot application runs on
EXPOSE 8085

# Define the entrypoint to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
