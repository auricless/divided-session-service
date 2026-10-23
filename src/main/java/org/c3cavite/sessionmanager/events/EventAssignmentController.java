package org.c3cavite.sessionmanager.events;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/assignment")
class EventAssignmentController {
  private final EventAssignmentService assignments;

  EventAssignmentController(EventAssignmentService assignments) {
    this.assignments = assignments;
  }

  @PostMapping
  EventAssignmentResponse assign(
      @PathVariable("eventId") UUID eventId, @RequestBody EventAssignmentRequest request) {
    return assignments.assign(eventId, request);
  }

  @GetMapping
  ResponseEntity<EventAssignmentResponse> latest(@PathVariable("eventId") UUID eventId) {
    return assignments
        .latest(eventId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }
}
