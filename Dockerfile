# Stage 1: Build the application using Maven and Eclipse Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and resolve dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and package the application (skipping tests for build speed)
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image using Eclipse Temurin JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the compiled JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose default ports
EXPOSE 8080 10000

# Start the application with dynamic PORT support
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
