package com.itstrat.acmf.apis.dto;


import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ProjectDTO {
    private Long id;
    private String name;
    private String category;
    private String description;
    private Long adminId;
    private List<String> tags;
    private String githubUrl;
    private LocalDateTime createdAt;
    private String rootDirectoryName;
    private String port;

}