package com.nineforce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Profile("dev")
@Configuration
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
        return http
              .csrf(csrf -> csrf.disable()) // keep it simple for dev (no CSRF token on your form)
              .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**").permitAll()
                        .anyRequest().authenticated()
              )
             // Use Spring's default login page; POST /login with username/password
              .formLogin(Customizer.withDefaults())
             .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
             .build();
        }
}
