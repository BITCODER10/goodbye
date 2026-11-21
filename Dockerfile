# ---- Stage 1: Build ----
# Use a Maven image that includes JDK 21 to compile the project
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory inside the container
WORKDIR /app

# Copy ONLY the pom.xml first, then download dependencies
# WHY? Docker caches each layer. If pom.xml hasn't changed, Docker
# reuses the cached dependencies instead of re-downloading them.
# This makes subsequent builds MUCH faster (seconds vs minutes).
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source code and build
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: Run ----
# Use a slim JRE image (no compiler, no Maven — just what's needed to run)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy ONLY the compiled JAR from the build stage
# The *.jar glob handles the version number in the filename
COPY --from=build /app/target/*.jar app.jar

# Document which port the app uses (doesn't actually publish it)
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]