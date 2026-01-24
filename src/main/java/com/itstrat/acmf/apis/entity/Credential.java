package com.itstrat.acmf.apis.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Credential {
    private String dockerUserName;
    private String dockerImageName;
}
