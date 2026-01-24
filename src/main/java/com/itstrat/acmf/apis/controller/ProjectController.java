package com.itstrat.acmf.apis.controller;

import com.itstrat.acmf.apis.Response.MessageResponse;
import com.itstrat.acmf.apis.dto.ProjectDTO;
import com.itstrat.acmf.apis.entity.JdlRequest;
import com.itstrat.acmf.apis.entity.MicroserviceJdlRequest;
import com.itstrat.acmf.apis.entity.Project;
import com.itstrat.acmf.apis.entity.User;
import com.itstrat.acmf.apis.exception.GitHubApiException;
import com.itstrat.acmf.apis.repository.ProjectRepository;
import com.itstrat.acmf.apis.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    @Autowired
    private DockerFileService dockerFileService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private GithubWorkflowService githubWorkflowService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JHipsterDockerService jhipsterDockerService;

    @GetMapping
    public ResponseEntity<List<ProjectDTO>> getProjects(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestHeader("Authorization") String jwt
    ) {
        try {
            User user = userService.findUserProfileByJwt(jwt);
            List<ProjectDTO> projectDTOs = projectService.getProjectByTeam(user, category, tag , null );
            return new ResponseEntity<>(projectDTOs, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error fetching user profile: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @GetMapping("/admin")
    public ResponseEntity<List<Project>>getProjectsByAdmin(
            @RequestHeader("Authorization") String jwt
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        List<Project> projects = projectService.getProjectsByAdmin(user);
        return  new ResponseEntity<>(projects , HttpStatus.OK);
    }

    @GetMapping("{projectId}")
    public ResponseEntity<Project>getProjectById(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String jwt
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        Project project= projectService.getProjectbyId(projectId);
        return  new ResponseEntity<>(project , HttpStatus.OK);
    }

    @GetMapping("/name/{projectName}")
    public ResponseEntity<Project>getProjectByName(
            @PathVariable String projectName,
            @RequestHeader("Authorization") String jwt
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        Project project = projectService.getProjectbyName(projectName);
        return  new ResponseEntity<>(project , HttpStatus.OK);
    }

    @GetMapping("/root/{rootDirectoryName}")
    public ResponseEntity<List<Project>> getProjectsByRootDirectoryName(
            @PathVariable String rootDirectoryName,
            @RequestHeader("Authorization") String jwt
    ) throws Exception {
        // validate user from JWT
        User user = userService.findUserProfileByJwt(jwt);

        // fetch projects
        List<Project> projects = projectService.getProjectsByRootDirectoryName(rootDirectoryName);

        return new ResponseEntity<>(projects, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Project>createProject(
            @RequestHeader("Authorization") String jwt,
            @RequestBody Project project
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        Project createdProject= projectService.createProject(project , user);
        return  new ResponseEntity<>(createdProject , HttpStatus.OK);
    }
    @PatchMapping("{projectId}")
    public ResponseEntity<Project>updateProject(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String jwt
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        Optional<Project> project = projectRepository.findById(projectId);
        Project updatedProject = null;
        if (project.isPresent()) {
            updatedProject= projectService.updateProject(project.get() , projectId);
        }
        return  new ResponseEntity<>(updatedProject , HttpStatus.OK);
    }
    @DeleteMapping("{projectId}")
    public ResponseEntity<MessageResponse>deleteProject(
            @PathVariable Long projectId,
            @RequestHeader("Authorization") String jwt
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        projectService.deleteProject(projectId , user.getId());
        MessageResponse res = new MessageResponse("Project Deleted Successfully");
        return  new ResponseEntity<>(res, HttpStatus.OK);
    }
    @GetMapping("/search")
    public ResponseEntity<List<Project>>searchProject(
            @RequestParam(required = false) String keyword,
            @RequestHeader("Authorization") String jwt
    )throws Exception
    {
        User user = userService.findUserProfileByJwt(jwt);
        List <Project> projects= projectService.searchProject(keyword , user);
        return  new ResponseEntity<>(projects , HttpStatus.OK);
    }

    @GetMapping("/monoliths")
    public ResponseEntity<List<ProjectDTO>> getMonolithicProjects(
            @RequestHeader("Authorization") String jwt
    ) {
        try {
            User user = userService.findUserProfileByJwt(jwt);
            List<ProjectDTO> projectDTOs = projectService.getMonolithicProjects(user);
            return new ResponseEntity<>(projectDTOs, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error fetching monolithic projects: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/microservices")
    public ResponseEntity<Map<String, List<ProjectDTO>>> getMicroserviceProjects(
            @RequestHeader("Authorization") String jwt
    ) {
        try {
            User user = userService.findUserProfileByJwt(jwt);
            Map<String, List<ProjectDTO>> groupedProjects = projectService.getMicroserviceAndGatewayProjectsGrouped(user);
            return new ResponseEntity<>(groupedProjects, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error fetching microservice projects: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    /**
     * REST endpoint to generate a new JHipster project, configure deployment,
     * and push the generated project to a GitHub repository.
     *
     * <p>This endpoint performs the following steps:
     * <ul>
     *     <li>Validates whether the project already exists in the system.</li>
     *     <li>Generates the JHipster project using Docker.</li>
     *     <li>Creates a Dockerfile and configures cloud deployment if required.</li>
     *     <li>Sets up GitHub workflows and pushes the project to a GitHub repository.</li>
     *     <li>Saves the project details in the database and associates it with the authenticated user.</li>
     * </ul>
     * </p>
     *
     * @param jdlRequest          The JHipster JDL request containing project configuration.
     * @param projectPath         Base path where the generated project will be stored temporarily.
     * @param githubUsername      GitHub username for repo creation and push.
     * @param githubToken         GitHub personal access token for authentication.
     * @param githubOrganization  (Optional) GitHub organization under which the repo should be created.
     * @param cloudProvider       (Optional) Cloud provider name (e.g., AWS, Azure, GCP).
     * @param cloudService        (Optional) Cloud service name for deployment (e.g., ECS, AppEngine).
     * @param accountId           (Optional) Cloud account ID for deployment.
     * @param region              (Optional) Cloud region for deployment.
     * @param jwt                 JWT token from request header for user authentication.
     * @return ResponseEntity<String> Success message or error details.
     */
    @PostMapping("/generate-project")
    public ResponseEntity<String> generateJHipsterProject(
            @RequestBody JdlRequest jdlRequest,
            @RequestParam String projectPath,
            @RequestParam String githubUsername,
            @RequestParam String githubToken,
            @RequestParam(required = false) String githubOrganization,
            @RequestParam(required = false) String cloudProvider,
            @RequestParam(required = false) String cloudService,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String region,
            @RequestHeader("Authorization") String jwt) {

        String newProjectPath = null;
        try {
            // Step 1: Log start of project generation
            logger.info("Starting project generation for baseName: {}", jdlRequest.getBaseName());
            String appBaseName = jdlRequest.getBaseName();

            // Step 2: Check if project with same name already exists
            if (projectRepository.existsByName(appBaseName)) {
                logger.warn("Project '{}' already exists.", appBaseName);
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Project already exists.");
            }

            // Step 3: Check if the host port is already taken
            if (projectRepository.existsByPort(jdlRequest.getServerPort())) {
                logger.warn("Project with port '{}' already exists.", jdlRequest.getServerPort());
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Project with port " + jdlRequest.getServerPort() + " already exists.");
            }

            // Step 3: Create project directory
            newProjectPath = projectPath + File.separator + appBaseName;
            Files.createDirectories(Paths.get(newProjectPath));

            // Step 4: Generate JHipster project using Docker
            jhipsterDockerService.generateMonolithProjectViaDocker(jdlRequest, newProjectPath);

            // Step 5: Create Dockerfile based on build tool
            dockerFileService.generateDockerfile(newProjectPath, jdlRequest.getBuildTool());

            // Step 7: Handle deployment configurations (cloud provider + service)
            deploymentService.applicationDeployment(
                    appBaseName,
                    newProjectPath,
                    cloudProvider,
                    cloudService,
                    accountId,
                    region,
                    githubOrganization,
                    jdlRequest.getApplicationType()
            );

            // Step 8: Create GitHub workflow for CI/CD
            githubWorkflowService.createGithubWorkflow(
                    appBaseName,
                    newProjectPath,
                    cloudProvider,
                    cloudService,
                    accountId,
                    region,
                    githubOrganization,
                    jdlRequest.getBuildTool(),
                    jdlRequest.getServerPort()
            );

            // Step 9: Create GitHub repository
            String repoUrl = createGitHubRepo(githubUsername, githubToken, appBaseName, githubOrganization);
            if (repoUrl.startsWith("https")) {
                // Step 10: Initialize Git and push project to GitHub
                initGitAndPushToGitHub(newProjectPath, repoUrl, githubUsername, githubToken);
            } else {
                throw new GitHubApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to create GitHub repository.");
            }

            // Step 11: Fetch authenticated user details using JWT
            User user = userService.findUserProfileByJwt(jwt);

            // Step 12: Save project details in database
            Project project = new Project();
            project.setName(appBaseName);
            project.setCategory(jdlRequest.getApplicationType());
            project.setDescription("Generated using JHipster");
            project.setAdmin(user);
            project.setGithubUrl(repoUrl);
            project.setTags(buildTags(jdlRequest));
            project.setPort(jdlRequest.getServerPort());
            projectService.createProject(project, user);

            logger.info("Monolith created and pushed to GitHub.");
            return ResponseEntity.ok("Project successfully created and pushed to GitHub.");

        } catch (Exception e) {
            // Handle all unexpected exceptions
            logger.error("Generation error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());

        } finally {
            // Step 13: Clean up local project directory after completion
            if(newProjectPath != null){
                deleteProjectDirectory(new File(newProjectPath));
            }
        }
    }

    private void downloadJHipsterProject(JdlRequest jdlRequest, String zipFilePath) throws IOException {
        String url = "https://start.jhipster.tech/api/download-application";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String jsonPayload = "{\"generator-jhipster\":" + jdlRequest.toJsonString() + "}";
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonPayload.getBytes());
        }

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(zipFilePath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            logger.info("Downloaded JHipster project ZIP to {}", zipFilePath);
        } else {
            throw new IOException("Failed to download JHipster project. HTTP response code: " + connection.getResponseCode());
        }
    }

    private void unzipProject(String zipFilePath, String destDir) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    new File(file.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipIn.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zipIn.closeEntry();
            }
        }
        Files.deleteIfExists(Paths.get(zipFilePath));
        logger.info("Unzipped JHipster project to {}", destDir);
    }




    private String createGitHubRepo(String username, String token, String repoName, String organizationName) throws IOException {
        String apiUrl;
        if (organizationName != null && !organizationName.isEmpty()) {

            apiUrl = "https://api.github.com/orgs/" + organizationName + "/repos";
        } else {

            apiUrl = "https://api.github.com/user/repos";
        }
        String jsonPayload = "{\"name\":\"" + repoName + "\", \"private\":false}";

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + token).getBytes()));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonPayload.getBytes("utf-8"));
        }

        int responseCode = connection.getResponseCode();
        StringBuilder responseBody = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                responseBody.append(line.trim());
            }
        } catch (IOException e) {
            // Capture error details in the response
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    responseBody.append(errorLine.trim());
                }
            }
        }

        if (responseCode == HttpURLConnection.HTTP_CREATED) {
            String response = responseBody.toString();
            logger.info("GitHub repository created successfully: {}", response);
            return response.split("\"clone_url\":\"")[1].split("\"")[0];
        } else {
            logger.error("Failed to create GitHub repository. Response code: {}. Response body: {}", responseCode, responseBody);
            return responseBody.toString();
        }
    }

    private void initGitAndPushToGitHub(String projectPath, String repoUrl, String username, String token) throws IOException, InterruptedException {
        runCommand(new File(projectPath), "git", "init");
        runCommand(new File(projectPath), "git", "add", ".");
        runCommand(new File(projectPath), "git", "commit", "-m", "Initial commit");
        runCommand(new File(projectPath), "git", "branch", "-M", "main");
        String remoteUrlWithAuth = repoUrl.replace("https://", "https://" + username + ":" + token + "@");
        runCommand(new File(projectPath), "git", "remote", "add", "origin", remoteUrlWithAuth);
        runCommand(new File(projectPath), "git", "push", "-u", "origin", "main");
    }

    private void runCommand(File workingDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true); // Redirect errors to the output stream for better logging
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Command output: {}", line);
            }
        }
        int exitCode = process.waitFor();
        logger.info("Command exited with code {}", exitCode);
    }

    private void deleteProjectDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteProjectDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
            logger.info("Deleted project directory: {}", directory.getAbsolutePath());
        }
    }

//    /**
//     * REST endpoint to generate multiple JHipster microservice projects inside a root directory,
//     * configure deployment, and push them to a single GitHub repository.
//     *
//     * <p>This endpoint performs the following workflow:</p>
//     * <ul>
//     *     <li>Creates a root directory for all microservices.</li>
//     *     <li>Iterates over each application defined in {@link MicroserviceJdlRequest}:</li>
//     *     <ul>
//     *         <li>Checks if the project already exists in the repository.</li>
//     *         <li>Generates the JHipster microservice project using Docker.</li>
//     *         <li>Creates a Dockerfile and GitHub workflow for each service.</li>
//     *         <li>Configures cloud deployment if requested.</li>
//     *         <li>Stores project metadata in the database (without GitHub URL initially).</li>
//     *     </ul>
//     *     <li>Initializes a single GitHub repository for the root directory and pushes all microservices.</li>
//     *     <li>Updates each project with its corresponding subdirectory GitHub URL.</li>
//     * </ul>
//     *
//     * @param microserviceJdlRequest Request object containing all applications and root directory name.
//     * @param projectPath            Base path where the root directory will be created.
//     * @param githubUsername         GitHub username for repository creation and push.
//     * @param githubToken            GitHub personal access token for authentication.
//     * @param githubOrganization     (Optional) GitHub organization name.
//     * @param cloudProvider          (Optional) Cloud provider name (e.g., AWS, Azure, GCP).
//     * @param cloudService           (Optional) Cloud service name (e.g., ECS, AppEngine).
//     * @param accountId              (Optional) Cloud account ID.
//     * @param region                 (Optional) Cloud region.
//     * @param jwt                    JWT token from request header for user authentication.
//     * @return ResponseEntity<String> Success message or error details.
//     */
//    @PostMapping("/generate-microservices")
//    public ResponseEntity<String> generateMicroservices(
//            @Valid @RequestBody MicroserviceJdlRequest microserviceJdlRequest,
//            @RequestParam String projectPath,
//            @RequestParam String githubUsername,
//            @RequestParam String githubToken,
//            @RequestParam(required = false) String githubOrganization,
//            @RequestParam(required = false) String cloudProvider,
//            @RequestParam(required = false) String cloudService,
//            @RequestParam(required = false) String accountId,
//            @RequestParam(required = false) String region,
//            @RequestHeader("Authorization") String jwt) {
//
//        // Root directory for all microservices
//        String rootDirPath = projectPath + File.separator + microserviceJdlRequest.getRootDirectoryName();
//        List<Project> savedProjects = new ArrayList<>();
//
//        try {
//            // Step 1: Create root directory for microservices
//            Files.createDirectories(Paths.get(rootDirPath));
//
//            // Step 2: Get authenticated user from JWT
//            User user = userService.findUserProfileByJwt(jwt);
//
//            // Step 3: Iterate over all microservice applications in request
//            for (JdlRequest app : microserviceJdlRequest.getApplications()) {
//                String appName = app.getBaseName();
//                String appPath = rootDirPath + File.separator + appName;
//
//                // Skip if project already exists
//                if (projectRepository.existsByName(appName)) {
//                    logger.warn("Project '{}' already exists. Skipping.", appName);
//                    continue;
//                }
//
//                // Create project directory
//                Files.createDirectories(Paths.get(appPath));
//
//                // Generate JHipster microservice project
//                jhipsterDockerService.generateMicroserviceProjectViaDocker(app, appPath);
//
//                // Generate Dockerfile for each service
//                dockerFileService.generateDockerfile(appPath, app.getBuildTool());
//
//                // Create GitHub workflow for CI/CD
//                githubWorkflowService.createGithubWorkflow(
//                        appName, appPath, cloudProvider, cloudService, accountId, region, githubOrganization, app.getBuildTool()
//                );
//
//                // Configure cloud deployment if requested
//                deploymentService.applicationDeployment(
//                        appName, appPath, cloudProvider, cloudService, accountId, region, githubOrganization, app.getApplicationType()
//                );
//
//                // Save project metadata locally (GitHub URL will be added later)
//                Project project = new Project();
//                project.setName(appName);
//                project.setCategory(app.getApplicationType());
//                project.setDescription("Generated using JHipster");
//                project.setAdmin(user);
//                project.setTags(buildTags(app));
//                savedProjects.add(project);
//            }
//
//            // Step 4: If all requested apps already exist, return conflict response
//            if (savedProjects.isEmpty()) {
//                return ResponseEntity.status(HttpStatus.CONFLICT).body("All requested baseNames already exist.");
//            }
//
//            // Step 5: Create a GitHub repository for the root directory
//            String repoUrl = createGitHubRepo(githubUsername, githubToken, microserviceJdlRequest.getRootDirectoryName(), githubOrganization);
//            if (!repoUrl.startsWith("https")) {
//                throw new GitHubApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "GitHub repository creation failed.");
//            }
//
//            // Step 6: Push the entire root directory to GitHub
//            initGitAndPushToGitHub(rootDirPath, repoUrl, githubUsername, githubToken);
//
//            // Step 7: Update each project with GitHub subdirectory URL and persist in DB
//            // Remove `.git` suffix from repoUrl if present
//            String cleanRepoUrl = repoUrl.endsWith(".git")
//                    ? repoUrl.substring(0, repoUrl.length() - 4)
//                    : repoUrl;
//
//            for (Project project : savedProjects) {
//                project.setRootDirectoryName(microserviceJdlRequest.getRootDirectoryName());
//                project.setGithubUrl(cleanRepoUrl + "/tree/main/" + project.getName());
//                projectService.createProject(project, project.getAdmin());
//            }
//
//            logger.info("Microservices generated and pushed to GitHub.");
//            return ResponseEntity.ok("All microservices created and pushed successfully.");
//
//        } catch (GitHubApiException e) {
//            // Specific error handling for GitHub API failures
//            logger.error("GitHub API Error: {}", e.getMessage());
//            return ResponseEntity.status(e.getStatusCode()).body("GitHub API Error: " + e.getMessage());
//
//        } catch (Exception e) {
//            // Catch-all for unexpected errors
//            logger.error("Generation Error: {}", e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
//
//        } finally {
//            // Step 8: Clean up local root directory
//            deleteProjectDirectory(new File(rootDirPath));
//        }
//    }


    /**
     * Generates multiple JHipster microservice projects into a single root directory (monorepo),
     * creates Dockerfiles per service, a single GitHub Actions workflow at the repo root,
     * cloud deployment descriptors (docker-compose for EC2 OR k8s manifests for EKS),
     * creates a single GitHub repo and pushes everything.
     */
    @PostMapping("/generate-microservices")
    public ResponseEntity<String> generateMicroservices(
            @Valid @RequestBody MicroserviceJdlRequest microserviceJdlRequest,
            @RequestParam String projectPath,
            @RequestParam String githubUsername,
            @RequestParam String githubToken,
            @RequestParam(required = false) String githubOrganization,
            @RequestParam(required = false) String cloudProvider,   // e.g., "AWS"
            @RequestParam(required = false) String cloudService,    // e.g., "EC2" or "EKS"
            @RequestParam(required = false) String accountId,       // AWS account id
            @RequestParam(required = false) String region,          // AWS region
            @RequestHeader("Authorization") String jwt) {

        // 0) Compute root directory for the monorepo
        String rootDirPath = projectPath + File.separator + microserviceJdlRequest.getRootDirectoryName();
        List<Project> savedProjects = new ArrayList<>();

        try {
            // 1) Create root directory
            Files.createDirectories(Paths.get(rootDirPath));

            // 2) Auth user
            User user = userService.findUserProfileByJwt(jwt);

            // 3) Iterate & create each microservice under the single root
            for (JdlRequest app : microserviceJdlRequest.getApplications()) {
                final String appName = app.getBaseName();
                final String appPath = rootDirPath + File.separator + appName;

                // Skip if this project name already exists (same as your monolith flow)
                if (projectRepository.existsByName(appName)) {
                    logger.warn("Project '" + appName + "' already exists. Skipping.");
                    continue;
                }

                // Create service directory
                Files.createDirectories(Paths.get(appPath));

                // Generate JHipster microservice via Docker
                jhipsterDockerService.generateMicroserviceProjectViaDocker(app, appPath);

                // Generate a Dockerfile per service (uses buildTool from JdlRequest)
                dockerFileService.generateDockerfile(appPath, app.getBuildTool());

                // NOTE: Do NOT create per-service workflows here (we’ll create ONE at root after the loop)

                // Optionally: any per-service infra scaffolding you still need can be done here

                // Build metadata to persist later
                Project p = new Project();
                p.setName(appName);
                p.setCategory(app.getApplicationType());
                p.setDescription("Generated using JHipster");
                p.setAdmin(user);
                p.setTags(buildTags(app));
                p.setPort(app.getServerPort());
                savedProjects.add(p);
            }

            if (savedProjects.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("All requested baseNames already exist.");
            }

            // 4) Create ONE GitHub Actions workflow at root for ALL services
            List<String> serviceNames = new ArrayList<>();
            for (JdlRequest app : microserviceJdlRequest.getApplications()) {
                serviceNames.add(app.getBaseName());
            }

            // Choose build tool per service at runtime (wrapper detection), so no need to pass build tool list
            githubWorkflowService.createGithubWorkflowForMicroservices(
                    rootDirPath,
                    serviceNames,
                    cloudProvider,
                    cloudService,
                    accountId,
                    region,
                    githubOrganization,
                    microserviceJdlRequest.getRootDirectoryName()
            );

            // 5) Cloud deployment descriptors (docker-compose for EC2 OR k8s for EKS)
            deploymentService.microservicesDeployment(
                    rootDirPath,
                    microserviceJdlRequest.getApplications(),
                    cloudProvider,
                    cloudService,
                    accountId,
                    region,
                    githubOrganization
            );

            // 6) Create GitHub repo for the ROOT directory (single repo)
            String repoUrl = createGitHubRepo(githubUsername, githubToken, microserviceJdlRequest.getRootDirectoryName(), githubOrganization);
            if (repoUrl == null || !repoUrl.startsWith("https")) {
                throw new GitHubApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "GitHub repository creation failed.");
            }

            // 7) Push monorepo
            initGitAndPushToGitHub(rootDirPath, repoUrl, githubUsername, githubToken);

            // 8) Persist each project with a subdirectory URL
            String cleanRepoUrl = repoUrl.endsWith(".git") ? repoUrl.substring(0, repoUrl.length() - 4) : repoUrl;
            for (Project project : savedProjects) {
                project.setRootDirectoryName(microserviceJdlRequest.getRootDirectoryName());
                project.setGithubUrl(cleanRepoUrl + "/tree/main/" + project.getName());
                projectService.createProject(project, project.getAdmin());
            }

            logger.info("Microservices generated and pushed to GitHub (single workflow at root).");
            return ResponseEntity.ok("All microservices created and pushed successfully.");

        } catch (GitHubApiException e) {
            logger.warn("GitHub API Error: " + e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body("GitHub API Error: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Generation Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        } finally {
            // 9) Optional cleanup — keep as per your current behavior
            deleteProjectDirectory(new File(rootDirPath));
        }
    }

    /**
     * Builds a list of tags for a given JDL request.
     *
     * @param app JDL request representing a microservice application.
     * @return List of tags describing the project.
     */
    private List<String> buildTags(JdlRequest app) {
        List<String> tags = new ArrayList<>();
        tags.add("SpringBoot");
        tags.add(app.getBuildTool());
        tags.add(app.getDatabaseType());
        if (app.getClientFramework() != null && !app.getClientFramework().isEmpty()) {
            tags.add(app.getClientFramework());
        }
        return tags;
    }


}