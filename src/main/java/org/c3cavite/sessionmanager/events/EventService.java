package org.c3cavite.sessionmanager.events;

import java.util.List;
import java.util.UUID;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.c3cavite.sessionmanager.api.SessionConfigMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class EventService {
  private final EventRepository events;
  private final SessionConfigMapper configs;
  private final ObjectMapper json;
  private final ParticipantRepository participants;

  EventService(
      EventRepository events,
      SessionConfigMapper configs,
      ObjectMapper json,
      ParticipantRepository participants) {
    this.events = events;
    this.configs = configs;
    this.json = json;
    this.participants = participants;
  }

  public EventResponse create(EventRequest request) {
    validateName(request);
    var config = configs.toResponse(configs.toDomain(request.config()));
    return response(
        events.saveAndFlush(
            new EventEntity(request.name().strip(), json.writeValueAsString(config))));
  }

  @Transactional(readOnly = true)
  public List<EventResponse> list() {
    return events.findAllByOrderByNameAscIdAsc().stream().map(this::response).toList();
  }

  @Transactional(readOnly = true)
  public EventResponse get(UUID id) {
    return response(events.findById(id).orElseThrow(this::notFound));
  }

  public EventResponse update(UUID id, EventRequest request) {
    validateName(request);
    var config = configs.toResponse(configs.toDomain(request.config()));
    EventEntity event = events.lockById(id).orElseThrow(this::notFound);
    var domainConfig = configs.toDomain(config);
    participants
        .findAllByEventIdOrderByNameAscIdAsc(id)
        .forEach(p -> p.update(p.toDomain().forSession(domainConfig)));
    event.update(request.name().strip(), json.writeValueAsString(config));
    return response(events.saveAndFlush(event));
  }

  public void delete(UUID id) {
    events.delete(events.lockById(id).orElseThrow(this::notFound));
  }

  private void validateName(EventRequest request) {
    if (request == null
        || request.name() == null
        || request.name().isBlank()
        || request.name().strip().length() > 200) {
      throw new IllegalArgumentException(
          "Event name is required and must have at most 200 characters");
    }
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
  }

  private EventResponse response(EventEntity event) {
    return new EventResponse(
        event.id(),
        event.name(),
        json.readValue(event.configJson(), AssignmentRequest.Config.class),
        event.revision());
  }
}
