# Use a maintained Java 17 runtime image
FROM eclipse-temurin:17-jre-jammy

# Set working directory inside container
WORKDIR /app

# Copy built jar into the container
COPY target/db-switch-app-1.0.0.jar app.jar

# Expose Cloud Run's expected port
ENV PORT=8080
EXPOSE $PORT

# Start the Spring Boot application
ENTRYPOINT ["sh","-c","exec java -Xmx2g -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -jar app.jar"]
