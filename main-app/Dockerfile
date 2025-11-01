# Use the official OpenJDK image as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the jar file into the container at /app
COPY target/main-app-1.0.0.jar app.jar

# Copy the Firebase service account key into the container, the jar has diff. path
COPY src/main/resources/serviceAccountKey.json src/main/resources/serviceAccountKey.json
# shell cp the file each time
#COPY ../shared-web/src/main/resources/serviceAccountKey.json src/main/resources/serviceAccountKey.json


# Make port 8080 available to the world outside this container
ENV PORT=8080
EXPOSE $PORT

# Run the jar file
#ENTRYPOINT ["java", "-Xmx4g", "-jar", "app.jar"]

#ENTRYPOINT ["java", "-Xmx4g", "-Dserver.port=${PORT:-8080}", "-Dserver.address=0.0.0.0", "-jar","app.jar"]
ENTRYPOINT ["sh","-c","exec java -Xmx4g -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -jar app.jar"]
