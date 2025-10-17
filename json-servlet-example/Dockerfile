# Use a lightweight Java 17 runtime image
FROM eclipse-temurin:17-jre-alpine

# Add a non-root user for security in production
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Set the working directory inside the container
WORKDIR /app

# Copy the pre-built JAR file into the container
# Assuming the JAR file is located in the submodule's "target" directory
COPY target/*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]