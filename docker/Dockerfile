# Use a base image that supports easy installation of tools
FROM eclipse-temurin:17-jdk-jammy

# Install Docker CLI (Required for your app to run 'docker run jhipster...')
RUN apt-get update && \
    apt-get install -y docker.io && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR file (Ensure you run 'mvn clean package' first locally or in CI)
COPY target/acmf-apis.jar /app/acmf-apis.jar

# Your application.properties says port 8081
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "acmf-apis.jar"]