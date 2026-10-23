package org.c3cavite.sessionmanager.events;

import java.util.List;
import java.util.UUID;
import org.c3cavite.core.domain.Participant;
import org.c3cavite.core.domain.SessionConfig;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.c3cavite.sessionmanager.api.SessionConfigMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ParticipantService {
  private final EventRepository events;
  private final ParticipantRepository participants;
  private final SessionConfigMapper configs;
  private final ObjectMapper json;

  ParticipantService(
      EventRepository events,
      ParticipantRepository participants,
      SessionConfigMapper configs,
      ObjectMapper json) {
    this.events = events;
    this.participants = participants;
    this.configs = configs;
    this.json = json;
  }

  public ParticipantResponse create(UUID eventId, AssignmentRequest.ParticipantInput input) {
    EventEntity event = lock(eventId);
    SessionConfig config = config(event);
    Participant participant = validate(input, config);
    ParticipantEntity saved =
        participants.saveAndFlush(new ParticipantEntity(eventId, participant));
    invalidate(event);
    return response(saved, config);
  }

  @Transactional(readOnly = true)
  public List<ParticipantResponse> list(UUID eventId) {
    SessionConfig config = config(events.findById(eventId).orElseThrow(this::notFound));
    return participants.findAllByEventIdOrderByNameAscIdAsc(eventId).stream()
        .map(p -> response(p, config))
        .toList();
  }

  @Transactional(readOnly = true)
  public ParticipantResponse get(UUID eventId, UUID id) {
    SessionConfig config = config(events.findById(eventId).orElseThrow(this::notFound));
    return response(find(eventId, id), config);
  }

  public ParticipantResponse update(
      UUID eventId, UUID id, AssignmentRequest.ParticipantInput input) {
    EventEntity event = lock(eventId);
    SessionConfig config = config(event);
    ParticipantEntity participant = find(eventId, id);
    participant.update(validate(input, config));
    participants.saveAndFlush(participant);
    invalidate(event);
    return response(participant, config);
  }

  public void delete(UUID eventId, UUID id) {
    EventEntity event = lock(eventId);
    participants.delete(find(eventId, id));
    invalidate(event);
  }

  private Participant validate(AssignmentRequest.ParticipantInput input, SessionConfig config) {
    if (input == null
        || input.name() == null
        || input.name().strip().length() > 200
        || input.age() == null) {
      throw new IllegalArgumentException(
          "Participant name and integer age are required; name must have at most 200 characters");
    }
    return new Participant(input.name().strip(), input.age(), input.gender(), input.lifeStage())
        .forSession(config);
  }

  private EventEntity lock(UUID id) {
    return events.lockById(id).orElseThrow(this::notFound);
  }

  private ParticipantEntity find(UUID eventId, UUID id) {
    return participants.findByIdAndEventId(id, eventId).orElseThrow(this::notFound);
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Event or participant not found");
  }

  private SessionConfig config(EventEntity event) {
    return configs.toDomain(json.readValue(event.configJson(), AssignmentRequest.Config.class));
  }

  private void invalidate(EventEntity event) {
    event.rosterChanged();
    events.saveAndFlush(event);
  }

  private ParticipantResponse response(ParticipantEntity entity, SessionConfig config) {
    Participant participant = entity.toDomain().forSession(config);
    return new ParticipantResponse(
        entity.id(),
        participant.getName(),
        participant.getAge(),
        participant.getGender(),
        participant.getLifeStage(),
        participant.getAgeTier());
  }
}
