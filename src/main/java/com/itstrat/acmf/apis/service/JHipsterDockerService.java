package com.itstrat.acmf.apis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itstrat.acmf.apis.entity.JdlRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

@Service
public class JHipsterDockerService {

    /**
     * Generates a JHipster Monolith project using Docker.
     *
     * <p>This method will:</p>
     * <ul>
     *     <li>Create a project directory based on the provided path.</li>
     *     <li>Write the .yo-rc.json configuration file using the given {@link JdlRequest}.</li>
     *     <li>Run the JHipster Docker generator inside the created directory.</li>
     * </ul>
     *
     * @param request     JDL request containing the monolith configuration.
     * @param projectPath Directory path where the monolith project will be generated.
     * @throws IOException          If writing files or running Docker fails.
     * @throws InterruptedException If the Docker process is interrupted.
     */
    public void generateMonolithProjectViaDocker(JdlRequest request, String projectPath) throws IOException, InterruptedException {
        File appDir = new File(projectPath);
        writeYoRc(appDir, request);  // Generate .yo-rc.json file from JDL config
        runDocker(appDir);           // Execute JHipster generator inside Docker
    }

    /**
     * Generates a JHipster Microservice project using Docker.
     *
     * <p>This method will:</p>
     * <ul>
     *     <li>Create a project directory based on the provided path.</li>
     *     <li>Write the .yo-rc.json configuration file using the given {@link JdlRequest}.</li>
     *     <li>Run the JHipster Docker generator inside the created directory.</li>
     * </ul>
     *
     * @param request     JDL request containing the microservice configuration.
     * @param projectPath Directory path where the microservice project will be generated.
     * @throws IOException          If writing files or running Docker fails.
     * @throws InterruptedException If the Docker process is interrupted.
     */
    public void generateMicroserviceProjectViaDocker(JdlRequest request, String projectPath) throws IOException, InterruptedException {
        File appDir = new File(projectPath);
        writeYoRc(appDir, request);  // Generate .yo-rc.json file from JDL config
        runDocker(appDir);           // Execute JHipster generator inside Docker
    }


    /**
     * Writes the JHipster configuration file (.yo-rc.json) into the specified directory
     * based on the properties provided in the {@link JdlRequest}.
     *
     * <p>The .yo-rc.json file is required by the JHipster generator and contains
     * all necessary metadata such as application type, database type, build tool,
     * authentication type, etc. This method dynamically builds the JSON structure
     * according to the request.</p>
     *
     * @param dir     The directory where the .yo-rc.json file should be created.
     * @param request The {@link JdlRequest} containing JHipster application settings.
     * @throws IOException If there is an error writing the file.
     */
    private void writeYoRc(File dir, JdlRequest request) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode config = mapper.createObjectNode();

        // Root object for JHipster configuration
        ObjectNode gen = config.putObject("generator-jhipster");

        // Basic metadata
        gen.put("baseName", request.getBaseName());
        gen.put("applicationType", request.getApplicationType());
        gen.put("packageName", request.getPackageName());
        gen.put("authenticationType", request.getAuthenticationType());
        gen.put("buildTool", request.getBuildTool());

        // Optional client framework
        if (request.getClientFramework() != null) {
            gen.put("clientFramework", request.getClientFramework());
        }

        // Database configuration
        gen.put("databaseType", request.getDatabaseType());
        gen.put("prodDatabaseType", request.getProdDatabaseType());

        // Optional cache provider
        if (request.getCacheProvider() != null) {
            gen.put("cacheProvider", request.getCacheProvider());
        }

        // Optional server port
        if (request.getServerPort() != null) {
            gen.put("serverPort", request.getServerPort());
        }

        // Hibernate cache toggle
        gen.put("enableHibernateCache", request.isEnableHibernateCache());

        // Optional JWT secret key
        if (request.getJwtSecretKey() != null) {
            gen.put("jwtSecretKey", request.getJwtSecretKey());
        }

        // Optional service discovery type (Eureka, Consul, etc.)
        if (request.getServiceDiscoveryType() != null) {
            gen.put("serviceDiscoveryType", request.getServiceDiscoveryType());
        }

        // Reactive vs. traditional app
        if (request.getApplicationType().equals("gateway")) {
            gen.put("reactive", true);
        } else {
            gen.put("reactive", request.isReactive());
        }

        // Internationalization (i18n) settings
        gen.put("nativeLanguage", request.getNativeLanguage() != null ? request.getNativeLanguage() : "en");
        ArrayNode languages = mapper.createArrayNode();
        if (request.getOtherLanguages() != null && !request.getOtherLanguages().isEmpty()) {
            request.getOtherLanguages().forEach(languages::add);
        } else {
            languages.add("en");
        }
        gen.set("languages", languages);
        gen.put("enableTranslation", request.isEnableTranslation());

        // Micro-frontend setting
        gen.put("microfrontend", request.isMicrofrontend());

        // Websocket support (only for monoliths)
        if (request.getApplicationType().equals("monolith") && request.getWebsocket() != null) {
            gen.put("websocket", request.getWebsocket());
        }

        // Optional client package manager (npm/yarn/pnpm)
        if (request.getClientPackageManager() != null) {
            gen.put("clientPackageManager", request.getClientPackageManager());
        }

        // Optional client themes
        if (request.getClientTheme() != null && !request.getClientTheme().isEmpty()) {
            ArrayNode themes = mapper.createArrayNode();
            request.getClientTheme().forEach(themes::add);
            gen.set("clientTheme", themes);
        }

        // Optional test frameworks
        if (request.getTestFrameworks() != null && !request.getTestFrameworks().isEmpty()) {
            ArrayNode testFrameworks = mapper.createArrayNode();
            request.getTestFrameworks().forEach(testFrameworks::add);
            gen.set("testFrameworks", testFrameworks);
        }

        // Skips (set to false by default so nothing is skipped)
        gen.put("skipUserManagement", false);
        gen.put("skipClient", false);
        gen.put("skipServer", false);

        // Finally write the .yo-rc.json file into the project directory
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(dir, ".yo-rc.json"), config);
    }

    /**
     * Executes the JHipster generator inside a Docker container to scaffold
     * a new application in the given directory.
     *
     * <p>This method mounts the target application directory into the Docker
     * container, runs the JHipster generator with non-interactive flags, and
     * streams the output logs back to the console.</p>
     *
     * <p>Before execution, it validates that Docker is both installed and running.
     * If either condition fails, a {@link RuntimeException} is thrown.</p>
     *
     * @param appDir The application directory where the JHipster project will be generated.
     * @throws IOException If there is an error starting the Docker process or reading output.
     * @throws InterruptedException If the Docker process is interrupted while running.
     * @throws RuntimeException If Docker is not installed, not running, or if the JHipster generation fails.
     */
//    private void runDocker(File appDir) throws IOException, InterruptedException {
//        // Docker command to run JHipster inside the official Docker image
//        // -v mounts the target directory into the container (/home/jhipster/app)
//        // -w sets the working directory inside the container
//        // Flags: --force (overwrite files), --skip-install, --skip-git, --no-insight, --defaults (non-interactive)
//        String dockerCmd = String.format(
//                "docker run --rm -u root -v \"%s:/home/jhipster/app\" -w /home/jhipster/app " +
//                        "jhipster/jhipster:v8.11.0 jhipster --force --skip-install --skip-git --no-insight --defaults",
//                appDir.getAbsolutePath().replace("\\", "/")
//        );
//
//        // Detect host OS to determine how to invoke the shell
//        String os = System.getProperty("os.name").toLowerCase();
//        ProcessBuilder pb;
//        if (os.contains("win")) {
//            pb = new ProcessBuilder("cmd.exe", "/c", dockerCmd);
//        } else {
//            pb = new ProcessBuilder("/bin/bash", "-c", dockerCmd);
//        }
//
//        // Merge stderr into stdout for consistent log output
//        pb.redirectErrorStream(true);
//
//        // Validate Docker availability
//        if (!isDockerInstalled()) {
//            throw new RuntimeException("Docker is not installed or not in PATH. Please install Docker and try again.");
//        }
//        if (!isDockerRunning()) {
//            throw new RuntimeException("Docker is not running. Please start Docker Desktop and try again.");
//        }
//
//        // Start the Docker process
//        Process process = pb.start();
//
//        // Stream logs from the container in real time
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                System.out.println("[Docker] " + line);
//            }
//        }
//
//        // Wait for the process to finish and validate exit code
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//            throw new RuntimeException(
//                    "JHipster Docker generation failed for: " + appDir.getAbsolutePath() +
//                            "\nPossible causes:\n" +
//                            "- Invalid permissions to mount the folder.\n" +
//                            "- Docker image issues.\n" +
//                            "- Volume sharing not enabled in Docker settings."
//            );
//        }
//    }

    private void runDocker(File appDir) throws IOException, InterruptedException {
        // 1. Determine the path on the HOST machine (EC2)
        String hostRootPath = System.getenv("HOST_ROOT_PATH");
        String mountPath;

        if (hostRootPath != null && !hostRootPath.isEmpty()) {
            // We are running in Docker on EC2.
            // We must use the Host's path for the volume mount.
            // appDir.getName() gives us "test1"
            mountPath = hostRootPath + "/" + appDir.getName();
        } else {
            // Local development (Windows/Mac) or no Env Var set
            mountPath = appDir.getAbsolutePath().replace("\\", "/");
        }

        // 2. Construct the command using the correct Host Path
        String dockerCmd = String.format(
                "docker run --rm -i -u root " +
                        "-v /var/run/docker.sock:/var/run/docker.sock " + // Access Docker Daemon
                        "-v \"%s:/home/jhipster/app\" " +
                        "-w /home/jhipster/app " +
                        "jhipster/jhipster:v8.11.0 jhipster --force --skip-install --skip-git --no-insight --defaults",
                mountPath
        );

        System.out.println("Executing Docker Command: " + dockerCmd); // Debug log

        // Detect host OS to determine how to invoke the shell
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", dockerCmd);
        } else {
            pb = new ProcessBuilder("/bin/bash", "-c", dockerCmd);
        }

        // Merge stderr into stdout for consistent log output
        pb.redirectErrorStream(true);

        // ... (Rest of the validation and process start code remains the same)
        if (!isDockerInstalled()) {
            throw new RuntimeException("Docker is not installed...");
        }

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Docker] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("JHipster Docker generation failed...");
        }
    }

    /**
     * Check if Docker CLI is installed and accessible
     */
    private boolean isDockerInstalled() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if Docker daemon is running
     */
    private boolean isDockerRunning() {
        try {
            Process process = new ProcessBuilder("docker", "info").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}


