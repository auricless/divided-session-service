package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.sessionmanager.SessionManagerApplication;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class EventPersistenceTest {
  @Test
  void eventConfigurationSurvivesACompleteApplicationContextRestart() {
    EventResponse created;
    EventAssignmentResponse snapshot;
    try (var first =
        new SpringApplicationBuilder(SessionManagerApplication.class)
            .web(WebApplicationType.NONE)
            .run()) {
      created =
          first
              .getBean(EventService.class)
              .create(
                  new EventRequest(
                      "Restart acceptance",
                      new AssignmentRequest.Config(8, 5, null, null, null, null)));
      first
          .getBean(ParticipantService.class)
          .create(
              created.id(),
              new AssignmentRequest.ParticipantInput("Persistent attendee", 18, Gender.MALE, null));
      snapshot =
          first
              .getBean(EventAssignmentService.class)
              .assign(created.id(), new EventAssignmentRequest(List.of(), null));
      created = first.getBean(EventService.class).get(created.id());
    }
    try (var second =
        new SpringApplicationBuilder(SessionManagerApplication.class)
            .web(WebApplicationType.NONE)
            .run()) {
      EventService events = second.getBean(EventService.class);
      try {
        assertEquals(created, events.get(created.id()));
        assertEquals(1, second.getBean(ParticipantService.class).list(created.id()).size());
        assertEquals(
            snapshot,
            second.getBean(EventAssignmentService.class).latest(created.id()).orElseThrow());
      } finally {
        events.delete(created.id());
      }
    }
  }
}
