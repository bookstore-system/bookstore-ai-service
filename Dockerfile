# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies to cache them
COPY pom.xml .
# Assuming there is a parent pom or standard structure. If multimodule, copy whole src.
RUN mvn dependency:go-offline -B

# Copy the source code
COPY src ./src

# Build the application, skip tests since we run them in Jenkins
RUN mvn clean package -DskipTests

# Stage 2: Create a minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the default port (override in specific deployment if needed)
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
