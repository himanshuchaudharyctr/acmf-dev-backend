package com.itstrat.acmf.apis.entity;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

public class MicroserviceJdlRequest {

    @NotBlank(message = "Root directory name is required")
    private String rootDirectoryName;

    @NotEmpty(message = "At least one JdlRequest is required")
    @Valid
    private List<JdlRequest> applications;

    public String getRootDirectoryName() {
        return rootDirectoryName;
    }

    public void setRootDirectoryName(String rootDirectoryName) {
        this.rootDirectoryName = rootDirectoryName;
    }

    public List<JdlRequest> getApplications() {
        return applications;
    }

    public void setApplications(List<JdlRequest> applications) {
        this.applications = applications;
    }
}
