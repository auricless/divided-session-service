package org.c3cavite.sessionmanager.events;

import java.util.Optional;
import java.util.UUID;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.c3cavite.sessionmanager.api.AssignmentResponse;
import org.c3cavite.sessionmanager.api.SessionAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class EventAssignmentService {
  private final EventRepository events;
  private final ParticipantRepository participants;
  private final SessionAssignmentService assignments;
  private final ObjectMapper json;

  EventAssignmentService(
      EventRepository events,
      ParticipantRepository participants,
      SessionAssignmentService assignments,
      ObjectMapper json) {
    this.events = events;
    this.participants = participants;
    this.assignments = assignments;
    this.json = json;
  }

  public EventAssignmentResponse assign(UUID eventId, EventAssignmentRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Facilitator roster is required");
    }
    EventEntity event = events.lockById(eventId).orElseThrow(this::notFound);
    var config = json.readValue(event.configJson(), AssignmentRequest.Config.class);
    var roster =
        participants.findAllByEventIdOrderByNameAscIdAsc(eventId).stream()
            .map(ParticipantEntity::toInput)
            .toList();
    AssignmentResponse result =
        assignments.assign(
            new AssignmentRequest(
                config, roster, request.facilitators(), request.facilitatorExceptions()));
    event.assign(json.writeValueAsString(result));
    events.saveAndFlush(event);
    return new EventAssignmentResponse(event.id(), event.name(), event.revision(), result);
  }

  @Transactional(readOnly = true)
  public Optional<EventAssignmentResponse> latest(UUID eventId) {
    EventEntity event = events.findById(eventId).orElseThrow(this::notFound);
    return event.assignmentJson() == null
        ? Optional.empty()
        : Optional.of(
            new EventAssignmentResponse(
                event.id(),
                event.name(),
                event.revision(),
                json.readValue(event.assignmentJson(), AssignmentResponse.class)));
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
  }
}
