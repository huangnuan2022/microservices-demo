package com.microservices.demo.config.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain webSecurityCustomizer(HttpSecurity http) throws Exception {
        System.out.println("SecurityConfig has been initialized");
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/encrypt/**", "/decrypt/**"))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**", "/encrypt/**", "/decrypt/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
