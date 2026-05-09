# Stage 1: Build Spring Boot app with Maven and Java 21
FROM maven:3.9.5-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first for caching
COPY pom.xml .
RUN mvn -B dependency:resolve

# Copy full source
COPY . .

# Package the Spring Boot app
RUN mvn clean package -DskipTests

# Stage 2: Use a lightweight Debian-based JDK image to run the app
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy the jar file from builder stage
COPY --from=builder /app/target/ShipmentTracking-0.0.1-SNAPSHOT.jar app.jar

# Set timezone
ENV TZ=Asia/Kolkata
RUN apt-get update && apt-get install -y tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Expose port
EXPOSE 8087

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
