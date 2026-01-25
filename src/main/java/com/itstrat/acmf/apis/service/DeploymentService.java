package com.itstrat.acmf.apis.service;

import com.itstrat.acmf.apis.entity.JdlRequest;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.stream.Collectors;


@Service
public class DeploymentService {

    private static final Logger LOGGER = Logger.getLogger(DeploymentService.class.getName());

    public static void applicationDeployment(String appName, String newProjectPath, String cloudProvider, String cloudService , String awsAccountId , String region , String githubOrg, String applicationType) {


        if ("EC2".equalsIgnoreCase(cloudService)) {
            LOGGER.info("Starting JHipster Docker Compose deployment for app: " + appName);
            if (!runJHipsterDockerCompose(newProjectPath)) {
                LOGGER.severe("JHipster Docker Compose execution failed.");
                return;
            }
            updateImageName(newProjectPath, appName , awsAccountId , region);
        }

        if ("EKS".equalsIgnoreCase(cloudService)) {
            createKubernetesDirectory(newProjectPath , appName , applicationType , awsAccountId , region);
        }


    }

    private static void updateImageName(String newProjectPath , String appName, String accountId ,String region){
        Path dockerComposePath = Paths.get(newProjectPath, "docker-compose.yml");
        String newImageValue = String.format("public.ecr.aws/c4d3l3m6/%s:latest", appName);

        try {
            // Read the file content
            String content = Files.lines(dockerComposePath)
                    .map(line -> line.contains("image: " + appName) ? "  image: " + newImageValue : line)
                    .collect(Collectors.joining("\n"));

            // Write back to the file
            Files.write(dockerComposePath, content.getBytes());
            System.out.println("Updated docker-compose.yml successfully.");
        } catch (IOException e) {
            System.err.println("Error updating docker-compose.yml: " + e.getMessage());
        }
    }

    private static boolean runJHipsterDockerCompose(String newProjectPath) {
        try {
            // Start the JHipster Docker Compose process
            String dockerCmd = String.format(
                    "docker run --rm -i -v \"%s:/home/jhipster/app\" -w /home/jhipster/app " +
                            "jhipster/jhipster:v8.11.0 jhipster docker-compose",
                    new File(newProjectPath).getAbsolutePath()
            );
            ProcessBuilder processBuilder = createProcessBuilder(dockerCmd);
            processBuilder.redirectErrorStream(true);
//            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "jhipster docker-compose");
//            processBuilder.directory(new File(newProjectPath));

            Process process = processBuilder.start();
            LOGGER.info("JHipster Docker Compose command started successfully in: " + newProjectPath);

            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("[JHipster Prompt]: " + line);
                            LOGGER.info("Providing input: ../");
                            writer.write("../");
                            writer.newLine();
                            writer.flush();
                            LOGGER.info("Providing input: <Enter>");
                            writer.newLine();
                            writer.flush();
                            LOGGER.info("Providing input: <Enter>");
                            writer.newLine();
                            writer.flush();

                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error reading process output or writing input", e);
                }
            });

            // Thread to handle process error stream
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        LOGGER.severe("[JHipster Error]: " + errorLine);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error reading process error stream", e);
                }
            });

            outputThread.start();
            errorThread.start();

            outputThread.join();
            errorThread.join();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("JHipster Docker Compose completed successfully.");
                return true;
            } else {
                LOGGER.severe("JHipster Docker Compose failed with exit code: " + exitCode);
                return false;
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Error running JHipster Docker Compose", e);
            return false;
        }
    }

    public static void createKubernetesDirectory(String newProjectPath, String appName , String applicationType , String accountId , String region) {
            Path kubernetesPath = Paths.get(newProjectPath, "kubernetes");
            Path yoRcFilePath = kubernetesPath.resolve(".yo-rc.json");

            try {

                if (!Files.exists(kubernetesPath)) {
                    Files.createDirectories(kubernetesPath);
                    LOGGER.info("Kubernetes directory created at: " + kubernetesPath.toString());
                } else {
                    LOGGER.info("Kubernetes directory already exists: " + kubernetesPath.toString());
                }


                String yoRcContent = "{\n" +
                        "  \"generator-jhipster\": {\n" +
                        "    \"appsFolders\": [\"" + appName + "\"],\n" +
                        "    \"deploymentApplicationType\": \""+ applicationType +"\",\n" +
                        "    \"directoryPath\": \"../../\",\n" +
                        "    \"dockerPushCommand\": \"docker push\",\n" +
                        "    \"dockerRepositoryName\": \"\",\n" +
                        "    \"ingressDomain\": null,\n" +
                        "    \"ingressType\": null,\n" +
                        "    \"kubernetesNamespace\": \"default\",\n" +
                        "    \"kubernetesServiceType\": \"LoadBalancer\",\n" +
                        "    \"kubernetesStorageClassName\": null,\n" +
                        "    \"kubernetesUseDynamicStorage\": false,\n" +
                        "    \"monitoring\": \"no\",\n" +
                        "    \"serviceDiscoveryType\": \"no\"\n" +
                        "  }\n" +
                        "}";


                Files.write(yoRcFilePath, yoRcContent.getBytes());
                LOGGER.info(".yo-rc.json file created at: " + yoRcFilePath.toString());


//                ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "jhipster kubernetes");
//                processBuilder.directory(kubernetesPath.toFile());
//                Process process = processBuilder.start();
//                process.waitFor();

                // FIX: Use Docker to run JHipster Kubernetes generation
                String dockerCmd = String.format(
                        "docker run --rm -i -v \"%s:/home/jhipster/app\" -w /home/jhipster/app " +
                                "jhipster/jhipster:v8.11.0 jhipster kubernetes --force --skip-checks",
                        kubernetesPath.toAbsolutePath().toString()
                );

                ProcessBuilder processBuilder = createProcessBuilder(dockerCmd);
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();

                // Read output to prevent process blocking
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOGGER.info("[K8s Gen]: " + line);
                    }
                }

                process.waitFor();


                if (process.exitValue() == 0) {
                    LOGGER.info("Successfully executed the 'jhipster kubernetes' command.");
                } else {
                    LOGGER.warning("The 'jhipster kubernetes' command failed with exit code " + process.exitValue());
                }
                Path yamlFilePath = kubernetesPath.resolve(appName + "-k8s/" + appName + "-deployment.yml");
                Path serviceFilePath = kubernetesPath.resolve(appName +"-k8s/" + appName + "-service.yml");
                modifyDeploymentYaml(yamlFilePath, appName, accountId, region);
                modifyServiceYaml(serviceFilePath);

            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Error creating Kubernetes directory, writing .yo-rc.json, or executing the jhipster command", e);
            }
        }
    private static void modifyServiceYaml(Path serviceFilePath) {
        try {
            if (!Files.exists(serviceFilePath)) {
                LOGGER.warning("Service YAML file not found: " + serviceFilePath.toString());
                return;
            }

            List<String> lines = Files.readAllLines(serviceFilePath);
            List<String> modifiedLines = new ArrayList<>();

            boolean inSpecSection = false;
            boolean typeAdded = false;
            List<String> specBlock = new ArrayList<>();

            for (String line : lines) {
                if (line.trim().equals("spec:")) {
                    inSpecSection = true;
                    specBlock.add(line);
                    continue;
                }

                if (inSpecSection) {
                    if (!typeAdded && line.trim().startsWith("selector:")) {
                        specBlock.add("  type: LoadBalancer");
                        typeAdded = true;
                    }
                    specBlock.add(line);
                } else {
                    modifiedLines.add(line);
                }
            }

            if (!typeAdded && inSpecSection) {

                specBlock.add(1, "  type: LoadBalancer");
            }

            modifiedLines.addAll(specBlock);
            Files.write(serviceFilePath, modifiedLines);
            LOGGER.info("Updated service YAML file: " + serviceFilePath.toString());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error modifying service YAML file", e);
        }
    }


    private static void modifyDeploymentYaml(Path yamlFilePath, String appName, String accountId, String region) {
        try {
            if (!Files.exists(yamlFilePath)) {
                LOGGER.warning("Deployment YAML file not found: " + yamlFilePath.toString());
                return;
            }


            List<String> lines = Files.readAllLines(yamlFilePath);
            List<String> modifiedLines = new ArrayList<>();


            String imagePattern = "image: " + appName;
            String newImage = "image: " + accountId + ".dkr.ecr." + region + ".amazonaws.com/" + appName + ":latest";

            for (String line : lines) {
                if (line.trim().startsWith("image: ") && line.contains(appName)) {
                    modifiedLines.add(line.replace(imagePattern, newImage));
                } else {
                    modifiedLines.add(line);
                }
            }


            Files.write(yamlFilePath, modifiedLines);
            LOGGER.info("Updated deployment YAML file: " + yamlFilePath.toString());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error modifying deployment YAML file", e);
        }
    }
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Entry point for microservices deployment scaffolding.
     * EC2: create one docker-compose.yml at repo root with all services and images (public ECR).
     * EKS: create kubernetes/ with per-service Deployment & Service manifests (private ECR).
     */
    public void microservicesDeployment(String rootDirectoryPath,
                                        List<JdlRequest> microservices,
                                        String cloudProvider,
                                        String cloudService,
                                        String accountId,
                                        String region,
                                        String githubOrg) throws Exception {

        if ("AWS".equalsIgnoreCase(cloudProvider) && "EC2".equalsIgnoreCase(cloudService)) {
            generateDockerComposeForMicroservices(rootDirectoryPath, microservices);
        }

        if ("AWS".equalsIgnoreCase(cloudProvider) && "EKS".equalsIgnoreCase(cloudService)) {
            createKubernetesDirectoryForMicroservices(rootDirectoryPath, microservices, accountId, region);
        }
    }

    // ---------- EC2: docker-compose.yml at root ----------
    private void generateDockerComposeForMicroservices(String rootDirectoryPath, List<JdlRequest> microservices) throws Exception {
        Path dockerComposePath = Paths.get(rootDirectoryPath, "docker-compose.yml");
        List<String> lines = new ArrayList<>();
        lines.add("version: '3.8'");
        lines.add("services:");

//        lines.add("  postgres:");
//        lines.add("    image: postgres:17");
//        lines.add("    restart: unless-stopped");
//        lines.add("    ports:");
//        lines.add("      - \"5432:5432\"");
//        lines.add("    environment:");
//        lines.add("      - POSTGRES_USER=platform_admin");
//        lines.add("      - POSTGRES_PASSWORD=strongpassword");
//        lines.add("      - POSTGRES_DB=postgres");
//        lines.add("    volumes:");
//        lines.add("      - pgdata:/var/lib/postgresql/data");
//        lines.add("      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql");

        for (JdlRequest service : microservices) {
            String name = service.getBaseName();
            String containerPort = service.getServerPort(); // fixed
            if (service.getServerPort() == null || service.getServerPort().isBlank()) {
                throw new Exception("Server Port cannot be blank for service " + name);
            }
            String hostPort = service.getServerPort();
            String image = "public.ecr.aws/c4d3l3m6/" + name + ":latest";

            lines.add("  " + name + ":");
            lines.add("    image: " + image);
            lines.add("    container_name: " + name);
            lines.add("    restart: unless-stopped");
            lines.add("    ports:");
            lines.add("      - \"" + hostPort + ":"+ containerPort+ "\"");
            lines.add("    networks:");
            lines.add("      - app-network");
            lines.add("    environment:");
            lines.add("      - SPRING_PROFILES_ACTIVE=prod");
            lines.add("      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/" + name);
            lines.add("      - SERVER_SERVLET_SESSION_COOKIE_SECURE=false");
            lines.add("      - JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET=YzNmZGM5NzQyMzU4MGQ4NDM4NjgwOTc0ZWU3OTlmMjE5YjYwZGIwN2UyZTU1NjgwYThhZmI2N2UzZTAxZGU2Yzc2Y2VlODQ5N2IyNzhjNDY4ZGI2ZmMzNjI1YTA5OTk4ODg1ZTVlODdkNDU4NTI1MzM4MmZjYmE4ZTE3MGE2ZmQ=");
            lines.add("      - SPRING_DATASOURCE_USERNAME=" + name + "_user");
            lines.add("      - SPRING_DATASOURCE_PASSWORD=" + name + "_pass");
            lines.add("      - SPRING_CLOUD_CONFIG_ENABLED=false");
            lines.add("      - SPRING_CLOUD_CONFIG_FAIL_FAST=false");
            lines.add("      - SPRING_CONFIG_IMPORT=");
            lines.add("      - EUREKA_CLIENT_ENABLED=false");
        }
//        lines.add("volumes:");
//        lines.add("  pgdata:");
        lines.add("");
        lines.add("networks:");
        lines.add("  app-network:");
        lines.add("    external: true");

        try {
            Files.write(dockerComposePath, lines);
            LOGGER.info("docker-compose.yml created at: " + dockerComposePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating docker-compose.yml", e);
        }
    }

    // ---------- EKS: kubernetes/ manifests for each service ----------
    private void createKubernetesDirectoryForMicroservices(String rootDirectoryPath,
                                                           List<JdlRequest> microservices,
                                                           String accountId,
                                                           String region) {
        Path kubernetesPath = Paths.get(rootDirectoryPath, "kubernetes");
        try {
            if (!Files.exists(kubernetesPath)) {
                Files.createDirectories(kubernetesPath);
                LOGGER.info("Kubernetes directory created at: " + kubernetesPath);
            }

            for (JdlRequest svc : microservices) {
                String name = svc.getBaseName();
                String port = (svc.getServerPort() == null || svc.getServerPort().isBlank())
                        ? "8080" : svc.getServerPort();

                // Deployment
                Path deploymentFile = kubernetesPath.resolve(name + "-deployment.yml");
                List<String> dep = List.of(
                        "apiVersion: apps/v1",
                        "kind: Deployment",
                        "metadata:",
                        "  name: " + name,
                        "spec:",
                        "  replicas: 1",
                        "  selector:",
                        "    matchLabels:",
                        "      app: " + name,
                        "  template:",
                        "    metadata:",
                        "      labels:",
                        "        app: " + name,
                        "    spec:",
                        "      containers:",
                        "        - name: " + name,
                        "          image: " + accountId + ".dkr.ecr." + region + ".amazonaws.com/" + name + ":latest",
                        "          ports:",
                        "            - containerPort: " + port,
                        "          env:",
                        "            - name: SPRING_PROFILES_ACTIVE",
                        "              value: prod"
                );
                Files.write(deploymentFile, dep);

                // Service
                Path serviceFile = kubernetesPath.resolve(name + "-service.yml");
                List<String> svcYaml = List.of(
                        "apiVersion: v1",
                        "kind: Service",
                        "metadata:",
                        "  name: " + name,
                        "spec:",
                        "  type: LoadBalancer",
                        "  selector:",
                        "    app: " + name,
                        "  ports:",
                        "    - protocol: TCP",
                        "      port: " + port,
                        "      targetPort: " + port
                );
                Files.write(serviceFile, svcYaml);

                LOGGER.info("K8s manifests created for service: " + name);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating Kubernetes manifests for microservices", e);
        }
    }

    // ADD THIS METHOD AT THE BOTTOM OF YOUR CLASS
    private static ProcessBuilder createProcessBuilder(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            return new ProcessBuilder("/bin/bash", "-c", command);
        }
    }
}

