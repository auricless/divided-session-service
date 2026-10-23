package org.c3cavite.sessionmanager.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
  private final SessionAssignmentService assignments;

  SessionController(SessionAssignmentService assignments) {
    this.assignments = assignments;
  }

  @PostMapping("/assign")
  public AssignmentResponse assign(@RequestBody AssignmentRequest request) {
    return assignments.assign(request);
  }
}
