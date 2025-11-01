    package com.nineforce;

    import org.junit.jupiter.api.Test;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.test.context.SpringBootTest;
    import org.springframework.boot.test.web.client.TestRestTemplate;
    import org.springframework.beans.factory.annotation.Value;

    import static org.assertj.core.api.Assertions.assertThat;

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    public class ContactsControllerTest {

        @Value(value="${local.server.port}")
        private int port;

        @Autowired
        private TestRestTemplate restTemplate;
/*
        @Test
        public void greetingShouldReturnDefaultMessage() throws Exception {
            assertThat(this.restTemplate.getForObject("http://localhost:" +
                    port + "/contacts", String.class)).
                    containsAnyOf("John Smith");
        }

 */
    }
