package org.c3cavite.sessionmanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.c3cavite.core.domain.SessionConfig;
import org.c3cavite.core.service.GroupAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class SessionManagerApplicationTest {
  @LocalServerPort private int port;

  @Autowired private GroupAssignmentService assignments;

  @Test
  void bootsAnHttpServerAndWiresTheStandaloneCore() {
    assertTrue(port > 0);
    assertTrue(
        assignments
            .assign(SessionConfig.builder().capSize(6).build(), List.of(), List.of())
            .isFinal());
  }
}
