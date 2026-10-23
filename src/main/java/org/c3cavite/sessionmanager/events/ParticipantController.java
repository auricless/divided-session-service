package org.c3cavite.sessionmanager.events;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/participants")
class ParticipantController {
  private final ParticipantService participants;

  ParticipantController(ParticipantService participants) {
    this.participants = participants;
  }

  @PostMapping
  ResponseEntity<ParticipantResponse> create(
      @PathVariable("eventId") UUID eventId,
      @RequestBody AssignmentRequest.ParticipantInput request) {
    ParticipantResponse participant = participants.create(eventId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/events/" + eventId + "/participants/" + participant.id()))
        .body(participant);
  }

  @GetMapping
  List<ParticipantResponse> list(@PathVariable("eventId") UUID eventId) {
    return participants.list(eventId);
  }

  @GetMapping("/{id}")
  ParticipantResponse get(@PathVariable("eventId") UUID eventId, @PathVariable("id") UUID id) {
    return participants.get(eventId, id);
  }

  @PutMapping("/{id}")
  ParticipantResponse update(
      @PathVariable("eventId") UUID eventId,
      @PathVariable("id") UUID id,
      @RequestBody AssignmentRequest.ParticipantInput request) {
    return participants.update(eventId, id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable("eventId") UUID eventId, @PathVariable("id") UUID id) {
    participants.delete(eventId, id);
    return ResponseEntity.noContent().build();
  }
}
