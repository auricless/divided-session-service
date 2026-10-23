package org.c3cavite.sessionmanager.api;

import org.c3cavite.core.service.GroupAssignmentService;
import org.springframework.stereotype.Service;

/** One application boundary for both stateless requests and registered event rosters. */
@Service
public class SessionAssignmentService {
  private final GroupAssignmentService assignments;
  private final SessionAssignmentMapper mapper;

  SessionAssignmentService(GroupAssignmentService assignments, SessionAssignmentMapper mapper) {
    this.assignments = assignments;
    this.mapper = mapper;
  }

  public AssignmentResponse assign(AssignmentRequest request) {
    var input = mapper.toDomain(request);
    return mapper.toResponse(
        assignments.assign(
            input.config(), input.participants(), input.facilitators(), input.exceptions()));
  }
}
