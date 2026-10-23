package org.c3cavite.sessionmanager.events;

import java.net.URI;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/events")
class EventController {
  private final EventService events;

  EventController(EventService events) {
    this.events = events;
  }

  @PostMapping
  ResponseEntity<EventResponse> create(@RequestBody EventRequest request) {
    EventResponse event = events.create(request);
    return ResponseEntity.created(URI.create("/api/v1/events/" + event.id())).body(event);
  }

  @GetMapping
  List<EventResponse> list() {
    return events.list();
  }

  @GetMapping("/{id}")
  EventResponse get(@PathVariable("id") UUID id) {
    return events.get(id);
  }

  @PutMapping("/{id}")
  EventResponse update(@PathVariable("id") UUID id, @RequestBody EventRequest request) {
    return events.update(id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
    events.delete(id);
    return ResponseEntity.noContent().build();
  }
}
