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
package org.rd.fullstack.springbooteda.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Arrays;

import org.rd.fullstack.springbooteda.util.AuthentificationTokenFilter;
import org.rd.fullstack.springbooteda.util.Role;
import org.rd.fullstack.springbooteda.util.ExceptionHandlerAuthEntryPoint;
import org.rd.fullstack.springbooteda.util.JwtUtils;
import org.rd.fullstack.springbooteda.util.UserDetailsServiceImpl;
import org.rd.fullstack.springbooteda.util.UserUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.Filter;

@Configuration
@EnableMethodSecurity(
    securedEnabled = true,  // Determines whether the use of the @Secured annotation is allowed.
    jsr250Enabled = true,   // Determines whether using the @RoleAllowed annotation is allowed.
    prePostEnabled = true)  // Determines whether the use of Spring Security pre/post annotations is allowed.
public class SecurityConfig {

    private static final String[] AUTH_WHITELIST = {
        "/app/**",                // Our Nuxt/Vue app.
        "/auth/**",               // JWT token management and generation.
        "/swagger-ui/**",         // API implementation through the use of Swagger.
        "/graphql/**",            // GraphQL API implementation.
        "/v3/api-docs/**",        // The API documentation (OpenAPI).
        "/actuator/health/**",    // Probe for liveness/readiness only.
        "/actuator/info"          // Application info only.
    };

    public SecurityConfig() {
        super();
    }

    @Value("${org.rd.fullstack.springbooteda.secret}")
    private String secret;

    @Value("${org.rd.fullstack.springbooteda.expiration}")
    private int expiration;

    @Value("${org.rd.fullstack.springbooteda.authorities}")
    private String authorities;

    @Bean
    JwtUtils jwtUtils() {
        return new JwtUtils(secret, expiration, authorities);
    }

    @Bean
    ExceptionHandlerAuthEntryPoint exceptionHandlingAuthEntryPoint() {
        return new ExceptionHandlerAuthEntryPoint();
    }

    @Bean
    UserUtils userUtils() {
        UserUtils userUtils = new UserUtils();
        PasswordEncoder passwordEncoder = passwordEncoder();

        userUtils.add("root", passwordEncoder.encode("root"),
                Arrays.asList(Role.ROLE_SELECT, Role.ROLE_INSERT, Role.ROLE_UPDATE, Role.ROLE_DELETE));

        userUtils.add("support", passwordEncoder.encode("support"),
                Arrays.asList(Role.ROLE_SELECT, Role.ROLE_UPDATE));

        userUtils.add("guest", passwordEncoder.encode("guest"),
                Arrays.asList(Role.ROLE_SELECT));

        return userUtils;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new UserDetailsServiceImpl();
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
      return authConfig.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(withDefaults());

        // CSRF disabled: this API is stateless (JWT-based).
        http.csrf(csrf -> csrf
            .disable());

        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(AUTH_WHITELIST).permitAll()
            //.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated());

        http.exceptionHandling(except -> except
            .authenticationEntryPoint(exceptionHandlingAuthEntryPoint()));

        http.sessionManagement(sm -> sm
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authenticationProvider(authenticationProvider());

        http.addFilterBefore(authentificationTokenFilter(jwtUtils(), userDetailsService()),
                             UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private Filter authentificationTokenFilter(JwtUtils jwtUtils, UserDetailsService userDetailsService) {
        return new AuthentificationTokenFilter(jwtUtils, userDetailsService);
    }
}