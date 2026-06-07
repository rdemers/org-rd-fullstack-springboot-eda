/*
 * Copyright 2026; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    final private String CST_SECRET;
    final private int    CST_EXPIRATION;
    final private String CST_AUTHORITIES;

    protected JwtUtils() {
        super();
        this.CST_SECRET = null;
        this.CST_EXPIRATION = -1;
        this.CST_AUTHORITIES = null;
    }

    public JwtUtils(@Value("${org.rd.fullstack.springbooteda.secret}") String secret,
                    @Value("${org.rd.fullstack.springbooteda.expiration}") int expiration,
                    @Value("${org.rd.fullstack.springbooteda.authorities}") String authorities) {
        super();
        this.CST_SECRET = secret;
        this.CST_EXPIRATION = expiration;
        this.CST_AUTHORITIES = authorities;
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        String authorities            = authentication.getAuthorities()
                                                      .stream()
                                                      .map(GrantedAuthority::getAuthority)
                                                      .collect(Collectors.joining(","));
        return Jwts.builder()
                   .id(UUID.randomUUID().toString())
                   .subject(userPrincipal.getUsername())
                   .claim(CST_AUTHORITIES, authorities)
                   .issuedAt(new Date())
                   .expiration(new Date((new Date()).getTime() + CST_EXPIRATION))
                   .signWith(getSigningKey())
                   .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public List<Role> getAuthoritiesFromJwtToken(String token) {
        List<Role> roles = new ArrayList<Role>();
        String authorities = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload().get(CST_AUTHORITIES, String.class);
        if (authorities != null && ! authorities.isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(authorities, ",");
            while (tokenizer.hasMoreElements()) {
                String role = tokenizer.nextToken();
                try {
                    roles.add(Role.valueOf(role));
                } catch (IllegalArgumentException ex) {
                    logger.warn("Unknown role in JWT token: {}.", role);
                }
            }
        }
        return roles;
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}.", ex);
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}.", ex);
        } catch (ExpiredJwtException ex) {
            logger.error("JWT token is expired: {}.", ex);
        } catch (UnsupportedJwtException ex) {
            logger.error("JWT token is unsupported: {}.", ex);
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}.", ex);
        }

        return false;
    }

    public String decodeJwtToken(String jwtToken) {
        Jws<Claims> jws;
        try {
            jws = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(jwtToken);
        }
        catch (JwtException ex) {
            logger.error("Invalid JWT token: {}.", ex);
            return ex.getLocalizedMessage(); // Simplicity ... This is an example only.
        }
        return jws.getPayload().toString();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = CST_SECRET.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}