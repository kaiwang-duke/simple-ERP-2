package com.nineforce.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class FirebaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);


    @Value("${firebase.web-api-key:#{null}}")
    private String webApiKey;

    @EventListener(ApplicationReadyEvent.class)
    public void logWebApiKeyPrefix() {
        String prefix = (webApiKey == null) ? "<not set>" : webApiKey.substring(0, Math.min(8, webApiKey.length()));
        logger.info("Firebase WEB API key prefix in this app: {}", prefix);
    }

    @Bean
    public FirebaseApp firebaseApp() {
        logger.info("Starting FirebaseApp initialization");

        if (FirebaseApp.getApps().isEmpty()) {
            try {
                InputStream serviceAccount = new ClassPathResource("serviceAccountKey.json").getInputStream();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                logger.info("Initializing FirebaseApp with service account");
                FirebaseApp app = FirebaseApp.initializeApp(options);
                logger.info("FirebaseApp initialized successfully");
                return app;
            } catch (IOException e) {
                logger.error("Failed to initialize FirebaseApp", e);
                throw new RuntimeException("Failed to initialize FirebaseApp", e);
            }
        } else {
            logger.info("FirebaseApp already initialized");
            return FirebaseApp.getInstance();
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}