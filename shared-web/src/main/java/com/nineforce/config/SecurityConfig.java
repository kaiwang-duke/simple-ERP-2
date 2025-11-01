package com.nineforce.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * This is filter chaining. It seems come after webSecurityCustomizer. And some chain is
 * Springboot default. So it is complex to understand.  Kai 06/13/2024
 */

@Profile({"cloud","local"})
@Configuration
@EnableWebSecurity
public class SecurityConfig {


    private final FirebaseAuthenticationFilter firebaseAuthFilter;

    @Autowired
    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthFilter) {
        this.firebaseAuthFilter = firebaseAuthFilter;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf
                        // If your login form doesn't include the CSRF hidden field yet,
                        // ignore CSRF just for /login (you can remove this once you add the hidden token)
                        .ignoringRequestMatchers("/login", "/api/**")          // ← REST can stay stateless
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .authorizeHttpRequests((auths) -> auths
                        .requestMatchers(
                            "/login", "/logout",
                            "/css/**", "/js/**", "/images/**", "/favicon.ico",
                            "/resources/**", "/static/**", "/public/**", "/webui/**",
                            "/h2-console/**", "/api/**", "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                //.formLogin(form -> form
                //        .loginPage("/login")    // display login page, use custom login controller
                //        .permitAll()
                //)

                // We use a custom LoginController for GET/POST /login; disable Spring's form login & basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // Redirect unauthenticated requests to /login instead of returning 403
                .exceptionHandling(e -> e
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .anonymous(anonymous -> anonymous
                        .principal("guestUser") // Define a custom principal name for anonymous users
                        .authorities("ROLE_GUEST") // Define custom authorities
                )
                .sessionManagement(session -> session
                        .maximumSessions(37)
                        .maxSessionsPreventsLogin(true)
                );

        // Add custom filter for Firebase authentication
        http.addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
