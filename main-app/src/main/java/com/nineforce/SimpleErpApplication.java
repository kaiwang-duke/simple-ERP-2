package com.nineforce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// test comment   2
/*
  ./mvnw clean spring-boot:run -pl main-app\
  -Dspring-boot.run.main-class=com.nineforce.SimpleErpApplication \
  -Dspring-boot.run.profiles=local


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
public class SimpleErpApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleErpApplication.class, args);
	}

}
