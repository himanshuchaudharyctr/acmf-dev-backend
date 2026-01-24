package com.itstrat.acmf.apis.repository;

import com.itstrat.acmf.apis.entity.Project;
import com.itstrat.acmf.apis.entity.User;
import jdk.jfr.Registered;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project ,Long> {
    List<Project> findByAdmin(User user);
    List<Project> findByNameAndTeam(String name , User user);
    Project findByName(String name);
    @Query("SELECT p from Project p join p.team t where t=:user")
    List<Project>findProjectByTeam(@Param("user") User user);
    List<Project> findByTeamOrAdmin(User user , User admin);
    boolean existsByName(String name);
    boolean existsByPort(String port);
    List<Project> findByRootDirectoryName(String rootDirectoryName);


}
