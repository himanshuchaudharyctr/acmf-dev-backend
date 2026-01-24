# Docker file used during "docker build"
Docker files used to generate container image for spring-boot project. We have included two different approaches.

1. Dockerfile
Uses **eclipse-temurin:17-jdk-ubi9-minimal** as base image to package newly generated artifacts and create application specific container image.
1. Dockerfile_multi_stage
Here it is using multi stage approach. In first stage base image **maven:3-eclipse-temurin-17** is used for maven build and generating required artifacts. In second stage base image **eclipse-temurin:17-jdk-ubi9-minimal** is used to extract relevant artifacts and discard rest.
