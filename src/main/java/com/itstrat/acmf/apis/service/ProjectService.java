package com.itstrat.acmf.apis.service;

import com.itstrat.acmf.apis.dto.ProjectDTO;
import com.itstrat.acmf.apis.entity.Project;
import com.itstrat.acmf.apis.entity.User;
import com.nimbusds.jose.crypto.utils.ECChecks;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface ProjectService {

    Project createProject(Project project , User user) throws Exception;

    Project getProjectbyId(Long projectId) throws Exception;
    void deleteProject(Long projectId , Long userId) throws Exception;
    Project updateProject(Project updatedProject , Long id) throws Exception;
    List<Project> searchProject(String keyword , User user)throws Exception;
    List<Project> getProjectsByAdmin(User admin) throws Exception;
    List<ProjectDTO> getProjectByTeam(User user, String category, String tag , String githubUrl ) throws Exception;
    Project getProjectbyName(String Name) throws Exception;
    List<ProjectDTO> getMonolithicProjects(User user) throws Exception;
    Map<String, List<ProjectDTO>> getMicroserviceAndGatewayProjectsGrouped(User user) throws Exception;

    List<Project> getProjectsByRootDirectoryName(String rootDirectoryName);
}
