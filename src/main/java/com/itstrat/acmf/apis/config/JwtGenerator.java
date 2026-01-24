
package com.itstrat.acmf.apis.config;


import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class JwtGenerator{

    static SecretKey key=Keys.hmacShaKeyFor(JwtConstant.secret_key.getBytes());

    public static String generateToken(Authentication auth) {


        return Jwts.builder()
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime()+86400000))
                .claim("email",auth.getName())
                .signWith(key)
                .compact();


    }

    public static String getEmailFromJwtToken(String jwt) {

        jwt=jwt.substring(7);
        Claims claims= Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(jwt).getBody();
        return String.valueOf(claims.get("email"));

    }


}
