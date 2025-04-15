# Use a Maven-based image to build the JAR
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY . /app

# Build the application and skip tests to speed up the process
RUN mkdir -p /app/downloads && chmod 777 /app/downloads
RUN mvn clean package -DskipTests

# Use a lightweight JDK image to run the app
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Run the JAR file
CMD ["java", "-jar", "app.jar"]
