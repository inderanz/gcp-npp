# Dockerfile for payment-service-1
# Use an official Java runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the jar file
COPY target/payment-service-*.jar payment-service.jar

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "payment-service.jar"]
