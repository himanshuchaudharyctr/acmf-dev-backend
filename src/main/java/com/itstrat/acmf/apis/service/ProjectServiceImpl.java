package com.itstrat.acmf.apis.service;

import com.itstrat.acmf.apis.dto.ProjectDTO;
import com.itstrat.acmf.apis.entity.Project;
import com.itstrat.acmf.apis.entity.User;
import com.itstrat.acmf.apis.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
@Service
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserService userService;

    private ProjectDTO mapToDTO(Project project) {
        return new ProjectDTO(
                project.getId(),
                project.getName(),
                project.getCategory(),
                project.getDescription(),
                project.getAdmin().getId(),
                project.getTags(),
                project.getGithubUrl(),
                project.getCreatedAt(),
                project.getRootDirectoryName(),
                project.getPort()
        );
    }

    @Override
    public Project createProject(Project project , User user) throws Exception {
        project.setAdmin(user);
        return (Project) projectRepository.save(project);
    }

    @Override
    public List<Project> getProjectsByAdmin(User admin) { return
            projectRepository.findAll() .stream() .filter(project ->
                            project.getAdmin().getId().equals(admin.getId()))
                    .collect(Collectors.toList()); }

    @Override
    public List<ProjectDTO> getProjectByTeam(User user, String category, String tag , String githubUrl ) throws Exception {
        List<Project> projects = projectRepository.findByAdmin(user);

        if (category != null) {
            projects = projects.stream()
                    .filter(project -> project.getCategory().equals(category))
                    .collect(Collectors.toList());
        }
        if (tag != null) {
            projects = projects.stream()
                    .filter(project -> project.getTags().contains(tag))
                    .collect(Collectors.toList());
        }

        // Map to DTOs
        return projects.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }


    @Override
    public Project getProjectbyId(Long projectId) throws Exception {
        Optional<Project> optionalProject = projectRepository.findById(projectId);
        if(optionalProject.isEmpty())
        {
            throw  new Exception("Project Not found");
        }
        return optionalProject.get();
    }

    @Override
    public Project getProjectbyName(String name) throws Exception{
        Project optionalProject = projectRepository.findByName(name);
        if(optionalProject==null){
            throw  new Exception( "projct not found by name");
        }
        return  optionalProject;
    }

    public List<Project> getProjectsByRootDirectoryName(String rootDirectoryName) {
        return projectRepository.findByRootDirectoryName(rootDirectoryName);
    }


    @Override
    public void deleteProject(Long projectId, Long userId) throws Exception {
      getProjectbyId(projectId);
      projectRepository.deleteById(projectId);
    }

    @Override
    public Project updateProject(Project updatedProject, Long id) throws Exception {
        Project project = getProjectbyId(id);
        project.setName(updatedProject.getName());
        project.setCategory(updatedProject.getCategory());

        return (Project) projectRepository.save(project);
    }

    @Override
    public List<Project> searchProject(String keyword , User user) throws Exception {
        String partialName = "%" + keyword + "%";

        return projectRepository.findByNameAndTeam(keyword , user);
    }

    @Override
    public List<ProjectDTO> getMonolithicProjects(User user) throws Exception {
        List<Project> projects = projectRepository.findByAdmin(user);

        // ✅ Filter only monolithic projects
        projects = projects.stream()
                .filter(project -> "monolith".equalsIgnoreCase(project.getCategory()))
                .collect(Collectors.toList());

        return projects.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<ProjectDTO>> getMicroserviceAndGatewayProjectsGrouped(User user) throws Exception {
        List<Project> projects = projectRepository.findByAdmin(user);

        // ✅ Filter only microservice and gateway projects
        projects = projects.stream()
                .filter(project -> "microservice".equalsIgnoreCase(project.getCategory())
                        || "gateway".equalsIgnoreCase(project.getCategory()))
                .collect(Collectors.toList());

        // ✅ Group by rootDirectoryName and map to DTOs
        return projects.stream()
                .collect(Collectors.groupingBy(
                        Project::getRootDirectoryName,
                        Collectors.mapping(this::mapToDTO, Collectors.toList())
                ));
    }



}
