package com.itstrat.acmf.apis.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String Jwt;
    private String message;
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public String getJwt() {
        return Jwt;
    }
    public void setJwt(String jwt) {
        Jwt = jwt;
    }
}
