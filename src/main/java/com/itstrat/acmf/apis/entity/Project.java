package com.itstrat.acmf.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minidev.json.annotate.JsonIgnore;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    private String description;

    @ManyToOne
    @JoinColumn(name = "admin_id", nullable = true)
    private User admin;

    @ManyToMany(fetch = FetchType.EAGER)
    @JsonIgnore
    private List<User> team;


    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "tag")
    private List<String> tags;

    @Column(nullable = true)
    private String githubUrl;

    @Column(nullable = true, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = true)
    private String rootDirectoryName;

    @Column(nullable = false)
    private String port;

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

