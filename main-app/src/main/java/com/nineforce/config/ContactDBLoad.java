package com.nineforce.config;
import com.nineforce.repository.ContactRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
/*
 */
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class ContactDBLoad {
    @Bean
    CommandLineRunner initDatabase(ContactRepository repository) {

        return args -> {
            /*
            System.out.println("Preloading " + repository.save(new Contact("John Smith", "123-456-7890")));
            System.out.println("Preloading " + repository.save(new Contact("Samantha Davis", "098-765-4321")));
            System.out.println("Preloading " + repository.save(new Contact("Kai Wang", "919-909-8855")));
             */
        };
    }
    /*
         */
    @Value("${spring.custom.check:NOT_SET}")
    private String checkMsg;

    private static final Logger logger = LoggerFactory.getLogger(ContactDBLoad.class);

    @PostConstruct
    public void verifyProfile() {
        System.out.println("Profile test (System.out): " + checkMsg);
        logger.info("Profile test (Logger): {}", checkMsg);
    }
}

