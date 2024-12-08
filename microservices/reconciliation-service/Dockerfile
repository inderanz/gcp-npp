# Base image with OpenJDK
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the JAR file built by Maven
COPY target/reconciliation-service-0.0.1-SNAPSHOT.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
