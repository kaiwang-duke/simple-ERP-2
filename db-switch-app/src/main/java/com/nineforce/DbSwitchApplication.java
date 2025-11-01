package com.nineforce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/*
    1. Build shared-web module first and install to local maven repo
   ./mvnw clean install -pl shared-web


  ./mvnw clean spring-boot:run -pl db-switch-app\
  -Dspring-boot.run.main-class=com.nineforce.DbSwitchApplication \
  -Dspring-boot.run.profiles=local


  // inside a conainer needs to change later.  TBD
  docker run --rm \
         -e PORT=8080 \
         -e SPRING_PROFILES_ACTIVE=local \
         -e SPRING_DATASOURCE_URL='jdbc:postgresql://host.docker.internal:5432/test_db' \
         -e SPRING_DATASOURCE_USERNAME='kaiwang' \
         -e SPRING_DATASOURCE_PASSWORD='<your_password>' \
         -p 8080:8080 \
         gcr.io/eng-empire-470108-k1/erp01:latest

 */
@SpringBootApplication(scanBasePackages = "com.nineforce")
public class DbSwitchApplication {

    public static void main(String[] args) {
        System.setProperty("spring.application.name", "db-switch-app");
        SpringApplication.run(DbSwitchApplication.class, args);
    }

}
