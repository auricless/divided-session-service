package org.c3cavite.sessionmanager.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.c3cavite.sessionmanager.api.AssignmentRequest;

public record EventAssignmentRequest(
    List<AssignmentRequest.FacilitatorInput> facilitators,
    List<AssignmentRequest.ExceptionInput> facilitatorExceptions) {
  public EventAssignmentRequest {
    facilitators =
        facilitators == null ? null : Collections.unmodifiableList(new ArrayList<>(facilitators));
    facilitatorExceptions =
        facilitatorExceptions == null
            ? null
            : Collections.unmodifiableList(new ArrayList<>(facilitatorExceptions));
  }
}
