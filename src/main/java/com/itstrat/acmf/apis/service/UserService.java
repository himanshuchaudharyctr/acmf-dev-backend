package com.itstrat.acmf.apis.service;

import com.itstrat.acmf.apis.entity.User;

public interface UserService {
    User findUserProfileByJwt(String jwt) throws Exception;
    User findUserByEmail(String email) throws Exception;
    User findUserById(Long Id) throws Exception;
}
