# Amazon  build and deploy pipeline
This pipeline is using Github actions. It runs maven build, generate container and upload it to container registry. Pipeline is triggered on code push trigger to **master** branch. 


### Github Actions Steps

#### Checkout code
Checkout source code and prep it.
#### Setup JDK 17
Setup JDK 17 (Distribution: temurin) and cache maven.
#### Execute unit tests
Launch and execute unit tests.
#### SonarQube Code Scanning
static and dynamic analysis of a codebase to detect common code issues such as bugs and vulnerabilities.
#### OWASP CVE Scan agains National Vulnerability Database)
OWASP code scanning against NVD to detect most current CVEs.
#### Build with Maven
Java source code is builded using maven.
#### Configure AWS credentials
Configure AWS credentials using GitHub secrets.
#### Login to Amazon ECR
Login to Amazon ECR and establish connectivity with ECR.
#### Build, tag and push docker image to Amazon ECR
Build, tag and publish docker image to Amazon ECR. It uses GitHub secrets and registry output from previous step. Docker file is located in **docker** folder. 
