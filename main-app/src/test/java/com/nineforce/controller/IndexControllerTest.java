package com.nineforce.controller;

import com.nineforce.util.FirebaseAuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;


import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(IndexController.class)
@Import(IndexControllerTest.MockConfig.class)  // Import the TestConfiguration
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // This is the mock we defined in MockConfig
    @Autowired
    private FirebaseAuthUtil firebaseAuthUtil;

    @BeforeEach
    void setUp() {
        // Clear or reset mocks if needed
        Mockito.reset(firebaseAuthUtil);
    }

    @Test
    @WithMockUser  // or @WithMockUser(username = "bob", roles = {"USER"})
    void testIndex_ReturnsIndexViewWithModelAttributes() throws Exception {
        // Arrange
        String expectedEmail = "user@example.com";
        when(firebaseAuthUtil.getUserEmail()).thenReturn(expectedEmail);

        // Act & Assert
        mockMvc.perform(get("/index"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("userEmail", expectedEmail))
                .andExpect(model().attribute("title", "Management Dashboard 0.1"));
    }

    // Additional tests...

    /**
     * Test configuration class to define your mock beans
     */
    @TestConfiguration
    static class MockConfig {

        @Bean
        FirebaseAuthUtil firebaseAuthUtil() {
            // Return a regular Mockito mock here
            return Mockito.mock(FirebaseAuthUtil.class);
        }
    }
}
