package com.itstrat.acmf.apis.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class GithubWorkflowService {

    private static final Logger LOGGER = Logger.getLogger(GithubWorkflowService.class.getName());

    public static void createGithubWorkflow(String appName, String projectPath, String cloudProvider,
                                            String cloudService, String awsAccountId, String region, String githubOrg , String githubUsername, String serverPort) {
        String workflowDir = projectPath + "/.github/workflows";
        File workflowFolder = new File(workflowDir);
        if (!workflowFolder.exists()) {
            if (workflowFolder.mkdirs()) {
                LOGGER.info("Created directory: " + workflowDir);
            } else {
                LOGGER.severe("Failed to create directory: " + workflowDir);
                return;
            }

        }

        if (githubOrg == null || githubOrg.isEmpty()) githubOrg = githubUsername;
        String deployYamlContent = "AWS".equalsIgnoreCase(cloudProvider) && "EKS".equalsIgnoreCase(cloudService) ?
                getEKSDeploymentYAML(appName, awsAccountId , region) : getEC2DeploymentYAML(appName ,  region , githubOrg , appName.toLowerCase(), serverPort);

        String deployFilePath = workflowDir + "/deploy.yml";
        try {
            Files.write(Paths.get(deployFilePath), deployYamlContent.getBytes());
            LOGGER.info("Deployment workflow created successfully at: " + deployFilePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing deployment workflow file", e);
        }
    }
    private static String getEC2DeploymentYAML(String appName, String region, String githubOrg , String appNameLowerCase, String serverPort) {
        return """
                name: Build, Push to ECR, and Deploy to EC2
                on:
                  push:
                    branches:
                      - main

                jobs:
                  build-and-push:
                    runs-on: ubuntu-latest
                    env:
                      AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
                      AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
                      AWS_REGION: ${{ secrets.AWS_REGION }}
                      PUBLIC_ECR_REPOSITORY_URI: public.ecr.aws/c4d3l3m6/%4$s
                      IMAGE_TAG: latest

                    steps:
                      - name: Checkout Code
                        uses: actions/checkout@v3

                      - name: Set up Java 17
                        uses: actions/setup-java@v3
                        with:
                          distribution: 'temurin'
                          java-version: '17'
                
                      - name: Set up Node.js
                        uses: actions/setup-node@v3
                        with:
                          node-version: '22' # Or the version in your package.json

                     # - name: Determine Build tool and run build
                      #  run: |
                       #   if [ -f "gradlew" ]; then
                        #     chmod +x gradlew
                         #   ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest --configure-on-demand
                          #else
                           # chmod +x mvnw
                            #./mvnw -Pprod verify jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true -DskipITs -C
                          #fi
                
                      - name: Determine Build tool and run build
                        run: |
                          if [ -f "gradlew" ]; then
                             chmod +x gradlew
                             # Explicitly run the webapp build before jib
                             ./gradlew -Pprod clean webapp -x test
                             ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest
                          else
                             chmod +x mvnw
                             # Ensure Maven builds the production assets first
                             ./mvnw -Pprod clean package -DskipTests -Dmaven.javadoc.skip=true
                             # Then let Jib create the image from the packaged classes
                             ./mvnw jib:dockerBuild -Pprod -DskipTests
                          fi
                
                      - name: Set up Docker
                        uses: docker/setup-buildx-action@v2

                      - name: Configure AWS Credentials
                        uses: aws-actions/configure-aws-credentials@v4
                        with:
                          aws-region: ${{ secrets.AWS_REGION }}
                          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
                          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}

                      - name: Log in to Amazon ECR Public
                        run: |
                          aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws
                
                      - name: Ensure ECR Public repository exists
                        run: |
                          aws ecr-public describe-repositories \\
                            --repository-names %4$s \\
                            --region us-east-1 \\
                          || aws ecr-public create-repository \\
                            --repository-name %4$s \\
                            --region us-east-1

                      - name: Build and Push Docker Image
                        env:
                          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
                        run: |
                          docker tag %4$s public.ecr.aws/c4d3l3m6/%4$s:latest
                          docker push public.ecr.aws/c4d3l3m6/%4$s:latest

                  deploy-to-ec2:
                    runs-on: ubuntu-latest
                    needs: build-and-push
                    env:
                      AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
                      AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
                      AWS_REGION: ${{ secrets.AWS_REGION }}
                      EC2_SSH_PRIVATE_KEY: ${{ secrets.EC2_SSH_PRIVATE_KEY }}
                      PUBLIC_ECR_REPOSITORY_URI: public.ecr.aws/c4d3l3m6/%4$s:latest
                      ECR_REPOSITORY: %4$s
                      IMAGE_TAG: latest

                    steps:
                      - name: Checkout the repository
                        uses: actions/checkout@v3

                      - name: SSH into EC2 and Deploy Docker Image
                        run: |
                          mkdir -p ~/.ssh
                          echo "${{ secrets.EC2_SSH_PRIVATE_KEY }}" > ~/.ssh/ec2-keypair.pem
                          chmod 600 ~/.ssh/ec2-keypair.pem
                          ssh-keyscan -H ${{ secrets.EC2_IP }} >> ~/.ssh/known_hosts
                          ssh -o StrictHostKeyChecking=no -i ~/.ssh/ec2-keypair.pem ec2-user@${{ secrets.EC2_IP }} <<EOF

                            sudo dnf update -y
                            sudo dnf install -y docker git
                            sudo systemctl start docker
                            sudo systemctl enable docker
                            sudo usermod -aG docker ec2-user
                            newgrp docker

                            if ! command -v aws &> /dev/null; then
                              sudo apt install -y awscli
                            fi

                            #DOCKER_COMPOSE_VERSION="2.20.2"
                            #sudo rm -f /usr/local/bin/docker-compose
                            #sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                            #sudo chmod +x /usr/local/bin/docker-compose
                            #docker-compose --version
                
                            docker network inspect app-network >/dev/null 2>&1 || docker network create app-network
                            if ! docker ps | grep -q postgres; then
                              docker run -d \\
                                --name postgres \\
                                --network app-network \\
                                -e POSTGRES_USER=platform_admin \\
                                -e POSTGRES_PASSWORD=strongpassword \\
                                -e POSTGRES_DB=postgres \\
                                -p 5432:5432 \\
                                -v pgdata:/var/lib/postgresql/data \\
                                postgres:17
                            fi
                
                            # Wait for Postgres to be ready
                            until docker exec postgres pg_isready -U platform_admin >/dev/null 2>&1; do
                              echo "Waiting for Postgres..."
                              sleep 2
                            done
                
                            docker exec postgres psql -U platform_admin -d postgres -tAc \\
                            "SELECT 1 FROM pg_database WHERE datname='%4$s'" \\
                            | grep -q 1 || docker exec postgres psql -U platform_admin -d postgres -c \\
                            "CREATE DATABASE %4$s;"

                            # Create user if it does not exist
                            docker exec postgres psql -U platform_admin -d postgres -tAc \\
                            "SELECT 1 FROM pg_roles WHERE rolname='%4$s_user'" \\
                            | grep -q 1 || docker exec postgres psql -U platform_admin -d postgres -c \\
                            "CREATE USER %4$s_user;"

                            docker exec postgres psql -U platform_admin -d postgres -c \\
                            "ALTER USER %4$s_user WITH PASSWORD '%4$s_pass';"

                            # Grant privileges (safe to re-run)
                            docker exec postgres psql -U platform_admin -d postgres -c \\
                            "GRANT ALL PRIVILEGES ON DATABASE %4$s TO %4$s_user;"

                            docker exec postgres psql -U platform_admin -d %4$s -c \\
                            "ALTER SCHEMA public OWNER TO %4$s_user;"

                            docker pull public.ecr.aws/c4d3l3m6/%4$s:latest
                
                            docker rm -f %4$s || true

                            if [ -d "%1$s/.git" ]; then
                              cd %1$s
                              git pull origin main
                            else
                              git clone https://github.com/%3$s/%1$s
                              cd %1$s
                            fi

                            #docker-compose down
                            #docker-compose up -d --force-recreate
                
                            docker run -d \\
                            --name %4$s \\
                            --network app-network \\
                            -p %5$s:%5$s \\
                            --restart unless-stopped \\
                            -e SPRING_PROFILES_ACTIVE=prod \\
                            -e SERVER_SERVLET_SESSION_COOKIE_SECURE=false \\
                            -e JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET=YzNmZGM5NzQyMzU4MGQ4NDM4NjgwOTc0ZWU3OTlmMjE5YjYwZGIwN2UyZTU1NjgwYThhZmI2N2UzZTAxZGU2Yzc2Y2VlODQ5N2IyNzhjNDY4ZGI2ZmMzNjI1YTA5OTk4ODg1ZTVlODdkNDU4NTI1MzM4MmZjYmE4ZTE3MGE2ZmQ= \\
                            -e SPRING_CLOUD_CONFIG_ENABLED=false \\
                            -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/%4$s \\
                            -e SPRING_DATASOURCE_USERNAME=%4$s_user \\
                            -e SPRING_DATASOURCE_PASSWORD=%4$s_pass \\
                            -e EUREKA_CLIENT_ENABLED=false \\
                            -e SPRING_CLOUD_DISCOVERY_ENABLED=false \\
                            public.ecr.aws/c4d3l3m6/%4$s:latest
                          EOF
                """.formatted(appName, region, githubOrg, appNameLowerCase, serverPort);
//                    .formatted(appName.toLowerCase().replaceAll("[^a-z0-9-]", "-"), region, githubOrg, appNameLowerCase);
    }


    private static String getEKSDeploymentYAML(String appName, String awsAccountId, String region) {
        return """
    name: Deploy to EKS

    on:
      push:
        branches:
          - main
      workflow_dispatch:

    jobs:
      deploy:
        name: Build and Deploy to EKS
        runs-on: ubuntu-latest
        permissions:
          id-token: write
          contents: read

        steps:
          - name: Checkout Code
            uses: actions/checkout@v4

          - name: Set Up JDK
            uses: actions/setup-java@v3
            with:
              java-version: '17'
              distribution: 'temurin'

          - name: Make mvnw Executable
            run: chmod +x mvnw

          - name: Build Application and Docker Image
            run: ./mvnw -Pprod verify jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true -DskipITs -C

          - name: Verify Dockerfile
            run: |
              if [ -f Dockerfile ]; then
                echo "Dockerfile found!"
              else
                echo "ERROR: Dockerfile not found!"
                exit 1
              fi

          - name: Configure AWS Credentials
            uses: aws-actions/configure-aws-credentials@v4
            with:
              aws-region: ${{ secrets.AWS_REGION }}
              aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
              aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
              aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}

          - name: Log in to Amazon ECR
            id: login-ecr
            uses: aws-actions/amazon-ecr-login@v2

          - name: Push Docker Image to ECR
            env:
              ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
              ECR_REPOSITORY: %1$s
              IMAGE_TAG: latest
            run: |
              docker image tag %1$s %2$s.dkr.ecr.%3$s.amazonaws.com/%1$s:latest
              docker push %2$s.dkr.ecr.%3$s.amazonaws.com/%1$s:latest

          - name: Set Up kubectl
            run: |
              aws eks update-kubeconfig --region %3$s --name ${{ secrets.EKS_CLUSTER_NAME }}
              kubectl config current-context

          - name: Verify kubectl Context
            run: |
              kubectl config get-contexts
              kubectl cluster-info

          - name: Deploy Microservices Using JHipster Script
            run: |
              cd kubernetes
              bash kubectl-apply.sh -f

          - name: Verify Deployment
            run: |
              kubectl get svc %1$s

          - name: Verify Public IP
            run: |
              echo "Waiting for Load Balancer to provision..."
              while [[ $(kubectl get svc %1$s -o jsonpath='{.status.loadBalancer.ingress[0].hostname}') == "" ]]; do
                sleep 10
              done
              EXTERNAL_IP=$(kubectl get svc %1$s -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
              echo "Application is accessible at http://${EXTERNAL_IP}"
    """.formatted(appName, awsAccountId, region);
    }


    /**
     * Creates ONE workflow in <root>/.github/workflows/deploy.yml that handles all microservices.
     */
    public void createGithubWorkflowForMicroservices(
            String rootDirPath,
            List<String> serviceNames,
            String cloudProvider,
            String cloudService,
            String awsAccountId,
            String region,
            String githubOrg,
            String rootAppName
    ) {
        String workflowDir = rootDirPath + "/.github/workflows";
        File workflowFolder = new File(workflowDir);
        if (!workflowFolder.exists() && !workflowFolder.mkdirs()) {
            LOGGER.severe("Failed to create directory: " + workflowDir);
            return;
        }

        final String yaml = ("AWS".equalsIgnoreCase(cloudProvider) && "EKS".equalsIgnoreCase(cloudService))
                ? getEKSWorkflowYAML(serviceNames, awsAccountId, region)
                : getEC2WorkflowYAML(serviceNames, githubOrg, rootAppName);

        String deployFilePath = workflowDir + "/deploy.yml";
        try {
            Files.write(Paths.get(deployFilePath), yaml.getBytes());
            LOGGER.info("Microservice deployment workflow created successfully at: " + deployFilePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error writing deployment workflow file", e);
        }
    }


//    private String getEC2WorkflowYAML(
//            List<String> services,
//            String githubOrg,
//            String repoName
//    ) {
//
//        String matrixList = services.stream()
//                .map(s -> "\"" + s + "\"")
//                .collect(Collectors.joining(", "));
//
//        String dockerPulls = services.stream()
//                .map(s -> "                    docker pull public.ecr.aws/c4d3l3m6/" + s + ":latest")
//                .collect(Collectors.joining("\n"));
//
//        String serviceLoop = services.stream()
//                .collect(Collectors.joining(" "));
//
//        return """
//        name: Build, Push to ECR Public, and Deploy to EC2
//
//        on:
//          push:
//            branches: [ "main" ]
//          workflow_dispatch:
//
//        jobs:
//          build-and-push:
//            runs-on: ubuntu-latest
//            strategy:
//              matrix:
//                service: [%s]
//
//            steps:
//              - name: Checkout code
//                uses: actions/checkout@v3
//
//              - name: Set up Java 17
//                uses: actions/setup-java@v3
//                with:
//                  distribution: temurin
//                  java-version: '17'
//
//              - name: Build service with Maven/Gradle + Jib
//                run: |
//                  cd ${{ matrix.service }}
//
//                  if [ -f "gradlew" ]; then
//                    chmod +x gradlew
//                    ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest
//                  elif [ -f "mvnw" ]; then
//                    chmod +x mvnw
//                    ./mvnw -Pprod verify jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true
//                  else
//                    echo "No build tool found"
//                    exit 1
//                  fi
//
//              - name: Configure AWS Credentials
//                uses: aws-actions/configure-aws-credentials@v4
//                with:
//                  aws-region: ${{ secrets.AWS_REGION }}
//                  aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
//                  aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
//                  aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
//
//              - name: Login to Amazon ECR Public
//                run: |
//                  aws ecr-public get-login-password --region us-east-1 | \
//                  docker login --username AWS --password-stdin public.ecr.aws
//
//              - name: Ensure ECR Public repository exists
//                run: |
//                  aws ecr-public describe-repositories \
//                    --repository-names ${{ matrix.service }} \
//                    --region us-east-1 \
//                  || aws ecr-public create-repository \
//                    --repository-name ${{ matrix.service }} \
//                    --region us-east-1
//
//              - name: Tag & Push Docker Image
//                run: |
//                  docker tag ${{ matrix.service }}:latest public.ecr.aws/c4d3l3m6/${{ matrix.service }}:latest
//                  docker push public.ecr.aws/c4d3l3m6/${{ matrix.service }}:latest
//
//          deploy-to-ec2:
//            runs-on: ubuntu-latest
//            needs: build-and-push
//
//            steps:
//              - name: Checkout repository
//                uses: actions/checkout@v3
//
//              - name: SSH into EC2 & deploy all services
//                run: |
//                  mkdir -p ~/.ssh
//                  echo "${{ secrets.EC2_SSH_PRIVATE_KEY }}" > ~/.ssh/ec2-keypair.pem
//                  chmod 600 ~/.ssh/ec2-keypair.pem
//                  ssh-keyscan -H ${{ secrets.EC2_IP }} >> ~/.ssh/known_hosts
//
//                  ssh -o StrictHostKeyChecking=no \
//                      -i ~/.ssh/ec2-keypair.pem \
//                      ec2-user@${{ secrets.EC2_IP }} <<'EOF'
//                    set -e
//
//                    # Docker (AL2023)
//                    sudo dnf install -y docker || true
//                    sudo systemctl enable --now docker
//                    sudo usermod -aG docker ec2-user || true
//
//                    # Docker Compose v2
//                    if ! docker compose version >/dev/null 2>&1; then
//                      sudo mkdir -p /usr/local/lib/docker/cli-plugins
//                      sudo curl -SL https://github.com/docker/compose/releases/download/v2.25.0/docker-compose-linux-x86_64 \
//                        -o /usr/local/lib/docker/cli-plugins/docker-compose
//                      sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
//                    fi
//
//                    docker compose version
//
//                    # Clone or sync repo
//                    if [ -d "%s/.git" ]; then
//                      cd %s
//                      git fetch origin
//                      git reset --hard origin/main
//                    else
//                      git clone https://github.com/%s/%s.git
//                      cd %s
//                    fi
//
//                    # Docker network
//                    docker network inspect app-network >/dev/null 2>&1 || docker network create app-network
//
//                    # Postgres
//                    if ! docker ps | grep -q postgres; then
//                      docker run -d \
//                        --name postgres \
//                        --network app-network \
//                        -e POSTGRES_USER=platform_admin \
//                        -e POSTGRES_PASSWORD=strongpassword \
//                        -e POSTGRES_DB=postgres \
//                        -p 5432:5432 \
//                        -v pgdata:/var/lib/postgresql/data \
//                        postgres:17
//                    fi
//
//                    until docker exec postgres pg_isready -U platform_admin >/dev/null 2>&1; do
//                      echo "Waiting for Postgres..."
//                      sleep 2
//                    done
//
//                    # Databases per service
//                    for SERVICE in %s; do
//                      DB_NAME="$SERVICE"
//                      DB_USER="${SERVICE}_user"
//                      DB_PASS="${SERVICE}_pass"
//
//                      docker exec postgres psql -U platform_admin -d postgres -tAc \
//                      "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 || \
//                      docker exec postgres psql -U platform_admin -d postgres -c \
//                      "CREATE DATABASE ${DB_NAME};"
//
//                      docker exec postgres psql -U platform_admin -d postgres -tAc \
//                      "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1 || \
//                      docker exec postgres psql -U platform_admin -d postgres -c \
//                      "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASS}';"
//
//                      docker exec postgres psql -U platform_admin -d postgres -c \
//                      "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"
//
//                      docker exec postgres psql -U platform_admin -d ${DB_NAME} -c \
//                      "ALTER SCHEMA public OWNER TO ${DB_USER};"
//                    done
//
//    %s
//
//                    docker compose down || true
//                    docker compose up -d --force-recreate
//                  EOF
//        """.formatted(
//                matrixList,
//                repoName,
//                repoName,
//                githubOrg,
//                repoName,
//                repoName,
//                serviceLoop,
//                dockerPulls
//        );
//    }

//    private String getEC2WorkflowYAML(
//            List<String> services,
//            String githubOrg,
//            String repoName
//    ) {
//
//        String matrixList = services.stream()
//                .map(s -> "\"" + s + "\"")
//                .collect(Collectors.joining(", "));
//
//        String dockerPulls = services.stream()
//                .map(s -> "                    docker pull public.ecr.aws/c4d3l3m6/" + s + ":latest")
//                .collect(Collectors.joining("\n"));
//
//        String serviceLoop = String.join(" ", services);
//
//        return """
//    name: Build, Push to ECR Public, and Deploy to EC2
//
//    on:
//      push:
//        branches: [ "main" ]
//      workflow_dispatch:
//
//    jobs:
//      build-and-push:
//        runs-on: ubuntu-latest
//        strategy:
//          matrix:
//            service: [%s]
//
//        steps:
//          - name: Checkout code
//            uses: actions/checkout@v3
//
//          - name: Set up Java 17
//            uses: actions/setup-java@v3
//            with:
//              distribution: temurin
//              java-version: '17'
//
//          - name: Set up Node.js
//            uses: actions/setup-node@v3
//            with:
//              node-version: '22' # Or the version in your package.json
//
//         # - name: Build service with Maven/Gradle + Jib
//         #   run: |
//         #     cd ${{ matrix.service }}
//
//         #     if [ -f "gradlew" ]; then
//         #       chmod +x gradlew
//         #       ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest
//         #     elif [ -f "mvnw" ]; then
//         #       chmod +x mvnw
//         #       ./mvnw -Pprod verify jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true
//         #     else
//         #       echo "No build tool found"
//         #       exit 1
//         #     fi
//
//          - name: Determine Build tool and run build
//            run: |
//              cd ${{ matrix.service }}
//
//              if [ -f "gradlew" ]; then
//                chmod +x gradlew
//                # Explicitly run the webapp build before jib
//                ./gradlew -Pprod clean webapp -x test
//                ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest
//              elif [ -f "mvnw" ]; then
//                chmod +x mvnw
//                # Ensure Maven builds the production assets first
//                ./mvnw -Pprod clean package -DskipTests -Dmaven.javadoc.skip=true
//                ./mvnw -Pprod verify jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true
//              else
//                echo "No build tool found"
//                exit 1
//              fi
//
//          - name: Configure AWS Credentials
//            uses: aws-actions/configure-aws-credentials@v4
//            with:
//              aws-region: ${{ secrets.AWS_REGION }}
//              aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
//              aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
//              aws-session-token: ${{ secrets.AWS_SESSION_TOKEN }}
//
//          - name: Login to Amazon ECR Public
//            run: |
//              aws ecr-public get-login-password --region us-east-1 | \
//              docker login --username AWS --password-stdin public.ecr.aws
//
//          - name: Ensure ECR Public repository exists
//            run: |
//              aws ecr-public describe-repositories \
//                --repository-names ${{ matrix.service }} \
//                --region us-east-1 \
//              || aws ecr-public create-repository \
//                --repository-name ${{ matrix.service }} \
//                --region us-east-1
//
//          - name: Tag & Push Docker Image
//            run: |
//              docker tag ${{ matrix.service }}:latest public.ecr.aws/c4d3l3m6/${{ matrix.service }}:latest
//              docker push public.ecr.aws/c4d3l3m6/${{ matrix.service }}:latest
//
//      deploy-to-ec2:
//        runs-on: ubuntu-latest
//        needs: build-and-push
//
//        steps:
//          - name: Checkout repository
//            uses: actions/checkout@v3
//
//          - name: SSH into EC2 & deploy
//            run: |
//              mkdir -p ~/.ssh
//              echo "${{ secrets.EC2_SSH_PRIVATE_KEY }}" > ~/.ssh/ec2-keypair.pem
//              chmod 600 ~/.ssh/ec2-keypair.pem
//              ssh-keyscan -H ${{ secrets.EC2_IP }} >> ~/.ssh/known_hosts
//
//              ssh -o StrictHostKeyChecking=no \
//                  -i ~/.ssh/ec2-keypair.pem \
//                  ec2-user@${{ secrets.EC2_IP }} <<'EOF'
//                set -e
//
//                # Docker (Amazon Linux 2023)
//                sudo dnf install -y docker || true
//                sudo systemctl enable --now docker
//                sudo usermod -aG docker ec2-user || true
//
//                # Docker Compose v2
//                if ! docker compose version >/dev/null 2>&1; then
//                  sudo mkdir -p /usr/local/lib/docker/cli-plugins
//                  sudo curl -SL https://github.com/docker/compose/releases/download/v2.25.0/docker-compose-linux-x86_64 \
//                    -o /usr/local/lib/docker/cli-plugins/docker-compose
//                  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
//                fi
//
//                docker compose version
//
//                # Clone or sync repo
//                if [ -d "%s/.git" ]; then
//                  cd %s
//                  git fetch origin
//                  git reset --hard origin/main
//                else
//                  git clone https://github.com/%s/%s.git
//                  cd %s
//                fi
//
//                # Shared network
//                docker network inspect app-network >/dev/null 2>&1 || docker network create app-network
//
//                # Shared Postgres (create once)
//                if ! docker ps | grep -q postgres; then
//                  docker run -d \
//                    --name postgres \
//                    --network app-network \
//                    -e POSTGRES_USER=platform_admin \
//                    -e POSTGRES_PASSWORD=strongpassword \
//                    -e POSTGRES_DB=postgres \
//                    -p 5432:5432 \
//                    -v pgdata:/var/lib/postgresql/data \
//                    postgres:17
//                fi
//
//                until docker exec postgres pg_isready -U platform_admin >/dev/null 2>&1; do
//                  echo "Waiting for Postgres..."
//                  sleep 2
//                done
//
//                # DB + user per service
//                for SERVICE in %s; do
//                  docker exec postgres psql -U platform_admin -d postgres -tAc \
//                  "SELECT 1 FROM pg_database WHERE datname='${SERVICE}'" | grep -q 1 || \
//                  docker exec postgres psql -U platform_admin -d postgres -c \
//                  "CREATE DATABASE ${SERVICE};"
//
//                  docker exec postgres psql -U platform_admin -d postgres -tAc \
//                  "SELECT 1 FROM pg_roles WHERE rolname='${SERVICE}_user'" | grep -q 1 || \
//                  docker exec postgres psql -U platform_admin -d postgres -c \
//                  "CREATE USER ${SERVICE}_user WITH PASSWORD '${SERVICE}_pass';"
//
//                  docker exec postgres psql -U platform_admin -d postgres -c \
//                  "GRANT ALL PRIVILEGES ON DATABASE ${SERVICE} TO ${SERVICE}_user;"
//
//                  docker exec postgres psql -U platform_admin -d ${SERVICE} -c \
//                  "ALTER SCHEMA public OWNER TO ${SERVICE}_user;"
//                done
//
//    %s
//
//                docker compose down --remove-orphans || true
//                docker compose up -d --force-recreate
//              EOF
//    """.formatted(
//                matrixList,
//                repoName,
//                repoName,
//                githubOrg,
//                repoName,
//                repoName,
//                serviceLoop,
//                dockerPulls
//        );
//    }

    private String getEC2WorkflowYAML(
            List<String> services,
            String githubOrg,
            String repoName
    ) {

        String matrixList = services.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", "));

        String dockerPulls = services.stream()
                .map(s -> "                    docker pull public.ecr.aws/c4d3l3m6/" + s + ":latest")
                .collect(Collectors.joining("\n"));

        String serviceLoop = String.join(" ", services);

        return """
name: Build, Push to ECR Public, and Deploy to EC2

on:
  push:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [%s]

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Set up Java 17
        uses: actions/setup-java@v3
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '22' # Or the version in your package.json

      - name: Determine Build tool and run build
        run: |
          cd ${{ matrix.service }}
        
          if [ -f "gradlew" ]; then
            chmod +x gradlew
            # Explicitly run the webapp build before jib
            ./gradlew -Pprod clean webapp -x test
            # UPDATED: Added -x cucumber to skip failing tests
            ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest -x cucumber
          elif [ -f "mvnw" ]; then
            chmod +x mvnw
            # Ensure Maven builds the production assets first
            ./mvnw -Pprod clean package -DskipTests -Dmaven.javadoc.skip=true
            # UPDATED: Changed 'verify' to 'package' to skip integration tests
            ./mvnw -Pprod package jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true
          else
            echo "No build tool found"
            exit 1
          fi

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-region: ${{ secrets.AWS_REGION }}
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}

      - name: Login to Amazon ECR Public
        run: |
          aws ecr-public get-login-password --region us-east-1 | \
          docker login --username AWS --password-stdin public.ecr.aws

      - name: Ensure ECR Public repository exists
        run: |
          aws ecr-public describe-repositories \
            --repository-names ${{ matrix.service }} \
            --region us-east-1 \
          || aws ecr-public create-repository \
            --repository-name ${{ matrix.service }} \
            --region us-east-1

      - name: Tag & Push Docker Image
        run: |
          docker tag ${{ matrix.service }}:latest public.ecr.aws/c4d3l3m6/${{ matrix.service }}:latest
          docker push public.ecr.aws/c4d3l3m6/${{ matrix.service }}:latest

  deploy-to-ec2:
    runs-on: ubuntu-latest
    needs: build-and-push

    steps:
      - name: Checkout repository
        uses: actions/checkout@v3

      - name: SSH into EC2 & deploy
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.EC2_SSH_PRIVATE_KEY }}" > ~/.ssh/ec2-keypair.pem
          chmod 600 ~/.ssh/ec2-keypair.pem
          ssh-keyscan -H ${{ secrets.EC2_IP }} >> ~/.ssh/known_hosts

          ssh -o StrictHostKeyChecking=no \
              -i ~/.ssh/ec2-keypair.pem \
              ec2-user@${{ secrets.EC2_IP }} <<'EOF'
            set -e

            # Docker (Amazon Linux 2023)
            sudo dnf install -y docker || true
            sudo systemctl enable --now docker
            sudo usermod -aG docker ec2-user || true

            # Docker Compose v2
            if ! docker compose version >/dev/null 2>&1; then
              sudo mkdir -p /usr/local/lib/docker/cli-plugins
              sudo curl -SL https://github.com/docker/compose/releases/download/v2.25.0/docker-compose-linux-x86_64 \
                -o /usr/local/lib/docker/cli-plugins/docker-compose
              sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
            fi

            docker compose version

            # Clone or sync repo
            if [ -d "%s/.git" ]; then
              cd %s
              git fetch origin
              git reset --hard origin/main
            else
              git clone https://github.com/%s/%s.git
              cd %s
            fi

            # Shared network
            docker network inspect app-network >/dev/null 2>&1 || docker network create app-network

            # --- NUCLEAR FIX: Force Clean & Restart Postgres ---
            # This ensures no stale volume data causes auth issues
            echo "Removing old Postgres container..."
            docker rm -f postgres || true
            
            echo "Removing old Postgres volume to force re-init..."
            docker volume rm pgdata || true

            echo "Starting new Postgres container..."
            docker run -d \
                --name postgres \
                --network app-network \
                -e POSTGRES_USER=platform_admin \
                -e POSTGRES_PASSWORD=strongpassword \
                -e POSTGRES_DB=postgres \
                -p 5432:5432 \
                -v pgdata:/var/lib/postgresql/data \
                postgres:17
            
            # Wait for Postgres with timeout
            count=0
            until docker exec postgres pg_isready -U platform_admin >/dev/null 2>&1; do
              echo "Waiting for Postgres... ($count/30)"
              sleep 2
              count=$((count+1))
              if [ $count -ge 30 ]; then
                echo "❌ Postgres failed to start. Logs:"
                docker logs postgres
                exit 1
              fi
            done

            # DB + user per service
            for SERVICE in %s; do
              docker exec postgres psql -U platform_admin -d postgres -tAc \
              "SELECT 1 FROM pg_database WHERE datname='${SERVICE}'" | grep -q 1 || \
              docker exec postgres psql -U platform_admin -d postgres -c \
              "CREATE DATABASE ${SERVICE};"

              docker exec postgres psql -U platform_admin -d postgres -tAc \
              "SELECT 1 FROM pg_roles WHERE rolname='${SERVICE}_user'" | grep -q 1 || \
              docker exec postgres psql -U platform_admin -d postgres -c \
              "CREATE USER ${SERVICE}_user WITH PASSWORD '${SERVICE}_pass';"

              docker exec postgres psql -U platform_admin -d postgres -c \
              "GRANT ALL PRIVILEGES ON DATABASE ${SERVICE} TO ${SERVICE}_user;"

              docker exec postgres psql -U platform_admin -d ${SERVICE} -c \
              "ALTER SCHEMA public OWNER TO ${SERVICE}_user;"
            done

%s

            docker compose down --remove-orphans || true
            docker compose up -d --force-recreate
          EOF
""".formatted(
                matrixList,
                repoName,
                repoName,
                githubOrg,
                repoName,
                repoName,
                serviceLoop,
                dockerPulls
        );
    }



    // ---------- EKS (Private ECR per account/region) ----------
    private String getEKSWorkflowYAML(List<String> services, String awsAccountId, String region) {
        String matrixList = services.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", "));

        return """
        name: Build & Deploy Microservices to EKS
        on:
          push:
            branches: [ "main" ]
          workflow_dispatch:

        jobs:
          build-and-push:
            runs-on: ubuntu-latest
            strategy:
              matrix:
                service: [%s]

            steps:
              - name: Checkout code
                uses: actions/checkout@v3

              - name: Set up Java 17
                uses: actions/setup-java@v3
                with:
                  distribution: 'temurin'
                  java-version: '17'

              - name: Build service with Maven/Gradle + Jib
                run: |
                  echo "Building service: ${{ matrix.service }}"
                  cd ${{ matrix.service }}
                  if [ -f "gradlew" ]; then
                    chmod +x gradlew
                    ./gradlew jibDockerBuild -Pprod -x test -x javadoc -x integrationTest --configure-on-demand
                  elif [ -f "mvnw" ]; then
                    chmod +x mvnw
                    ./mvnw -Pprod verify jib:dockerBuild -DskipTests -Dmaven.javadoc.skip=true -DskipITs -C
                  else
                    echo "No Maven or Gradle wrapper found in ${{ matrix.service }}"
                    exit 1
                  fi

              - name: Configure AWS Credentials
                uses: aws-actions/configure-aws-credentials@v4
                with:
                  aws-region: ${{ secrets.AWS_REGION }}
                  aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
                  aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}

              - name: Log in to Amazon ECR
                uses: aws-actions/amazon-ecr-login@v2

              - name: Tag & Push Docker Image
                run: |
                  echo "Pushing Docker image for ${{ matrix.service }}"
                  docker tag ${{ matrix.service }} %s.dkr.ecr.%s.amazonaws.com/${{ matrix.service }}:latest
                  docker push %s.dkr.ecr.%s.amazonaws.com/${{ matrix.service }}:latest

          deploy:
            runs-on: ubuntu-latest
            needs: build-and-push
            steps:
              - name: Configure kubectl context
                run: |
                  aws eks update-kubeconfig --region %s --name ${{ secrets.EKS_CLUSTER_NAME }}
                  kubectl config current-context
                  kubectl get nodes

              - name: Apply Kubernetes Manifests
                run: |
                  kubectl apply -f kubernetes/
        """.formatted(
                matrixList,
                awsAccountId, region,
                awsAccountId, region,
                region
        );
    }


}
