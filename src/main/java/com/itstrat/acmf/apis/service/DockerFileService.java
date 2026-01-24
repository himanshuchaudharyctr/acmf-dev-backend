package com.itstrat.acmf.apis.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Service
public class DockerFileService {


    public void generateDockerfile(String projectPath, String buildTool) throws IOException {
        String dockerfilePath = projectPath + File.separator + "Dockerfile";
        String dockerContent;

        switch (buildTool.toLowerCase()) {
            case "gradle":
                dockerContent = """
                        FROM openjdk:21-jdk-slim
                        
                        WORKDIR /app
                        
                        COPY build/libs/*.jar app.jar
                        
                        EXPOSE 8080
                        
                        ENTRYPOINT ["java", "-jar", "app.jar"]
                        """;
                break;

            case "maven":
                dockerContent = """
                        FROM openjdk:21-jdk-slim
                        
                        WORKDIR /app
                        
                        COPY target/*.jar app.jar
                        
                        EXPOSE 8080
                        
                        ENTRYPOINT ["java", "-jar", "app.jar"]
                        """;
                break;

            default:
                throw new IllegalArgumentException("Unsupported build tool: " + buildTool);
        }

        try {
            Files.write(Paths.get(dockerfilePath), dockerContent.getBytes());
            System.out.println("Dockerfile successfully created at: " + dockerfilePath);
        } catch (IOException e) {
            System.err.println("Failed to create Dockerfile: " + e.getMessage());
            throw e;
        }
    }

    // ========== New microservices helper (per-service) ==========
    public void generateDockerfilesForMicroservices(String rootDirectoryPath, List<String> serviceNames, List<String> buildTools) throws IOException {
        if (serviceNames.size() != buildTools.size()) {
            throw new IllegalArgumentException("serviceNames and buildTools must be the same length.");
        }
        for (int i = 0; i < serviceNames.size(); i++) {
            String service = serviceNames.get(i);
            String buildTool = buildTools.get(i);
            String projectPath = rootDirectoryPath + File.separator + service;
            generateDockerfile(projectPath, buildTool);
        }
    }
}
