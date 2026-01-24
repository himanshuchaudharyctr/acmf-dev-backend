package com.itstrat.acmf.apis.controller;

import com.itstrat.acmf.apis.entity.User;
import com.itstrat.acmf.apis.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;
    @GetMapping("/profile")
    public ResponseEntity<User> getUserProfile(@RequestHeader("Authorization")String jwt ) throws Exception{
        User user = userService.findUserProfileByJwt(jwt);
        return new ResponseEntity<>(user , HttpStatus.OK);
    }


}
