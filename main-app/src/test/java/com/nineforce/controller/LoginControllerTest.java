package com.nineforce.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.nineforce.util.FirebaseAuthUtil;
import com.nineforce.util.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@WebMvcTest(controllers = LoginController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = FirebaseAuthUtil.class)
        }
)
@Import(LoginControllerTest.SecurityConfig.class)  // Import the TestConfigurations
@AutoConfigureMockMvc(addFilters = false)  // <-- disable all security filters in tests

public class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @MockitoBean
    private FirebaseAuthUtil firebaseAuthUtil;

    @Autowired
    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @BeforeEach
    public void setUp() {
        // Reset the mocks before each test, if you want a fresh state each time
        Mockito.reset(firebaseAuthUtil, firebaseAuth);
    }

    @Test
    public void testGetLogin() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    public void testPostLogin_Success() throws Exception {
        String email = "user@example.com";
        String password = "password";
        String idToken = "validIdToken";
        FirebaseToken firebaseToken = Mockito.mock(FirebaseToken.class);

        when(firebaseAuthUtil.authenticateUser(email, password)).thenReturn(idToken);
        when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);
        when(firebaseToken.getUid()).thenReturn("testUser");

        mockMvc.perform(MockMvcRequestBuilders.post("/login")
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/index"));
    }

    @Test
    public void testPostLogin_InvalidCredentials() throws Exception {
        String email = "user@example.com";
        String password = "wrongPassword";

        when(firebaseAuthUtil.authenticateUser(email, password))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(MockMvcRequestBuilders.post("/login")
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("error", "Invalid email or password"));
    }

    @Test
    public void testPostLogin_UnexpectedError() throws Exception {
        Logger logger = org.slf4j.LoggerFactory.getLogger(LoginController.class);
        // Temporarily turn OFF all logging from your controller package
        ch.qos.logback.classic.Level oldLevel  = null;
        ch.qos.logback.classic.Logger logbackLogger = null;
        if (logger instanceof ch.qos.logback.classic.Logger) {
            logbackLogger = (ch.qos.logback.classic.Logger) logger;
            oldLevel = logbackLogger.getLevel();
            // You can now work with the currentLevel
        }
        try {
            if (logbackLogger != null) {
                logbackLogger.setLevel(ch.qos.logback.classic.Level.OFF);
            }
            String email = "user@example.com";
            String password = "password";

            when(firebaseAuthUtil.authenticateUser(email, password))
               .thenThrow(new RuntimeException(
               "======= IGNORE: Unexpected error from test case: testPostLogin_UnexpectedError. ======="));

            mockMvc.perform(MockMvcRequestBuilders.post("/login")
                            .param("email", email)
                            .param("password", password))
                    .andExpect(status().isOk())
                    .andExpect(view().name("login"))
                    .andExpect(model().attribute("error", "An unexpected error occurred. Please try again."));
        } finally {
            // Restore original log level
            if (logbackLogger != null) {
                logbackLogger.setLevel(oldLevel);
            }
        }
    }

    @TestConfiguration
    static class SecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().permitAll() // Permit all requests for testing
                    )
                    .formLogin(form -> form
                            .loginPage("/login") // Use your controller's /login
                            .permitAll()
                    )
                    //.formLogin(Customizer.withDefaults()) // Configure form login with default settings
                    .csrf(AbstractHttpConfigurer::disable); // Disable CSRF for simplicity in tests
            return http.build();
        }
    }
}
