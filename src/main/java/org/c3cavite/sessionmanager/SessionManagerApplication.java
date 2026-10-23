package org.c3cavite.sessionmanager;

import org.c3cavite.core.service.GroupAssignmentService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SessionManagerApplication {
  public static void main(String[] args) {
    SpringApplication.run(SessionManagerApplication.class, args);
  }

  @Bean
  GroupAssignmentService groupAssignmentService() {
    return new GroupAssignmentService();
  }
}
