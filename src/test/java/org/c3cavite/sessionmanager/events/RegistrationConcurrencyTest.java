package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationConcurrencyTest {
  @Autowired private EventService events;
  @Autowired private ParticipantService participants;
  @Autowired private EventAssignmentService assignments;
  @Autowired private ParticipantRepository repository;

  @Test
  void concurrentRegistrationsAndAssignmentsKeepTheLatestSnapshotConsistent() throws Exception {
    var event =
        events.create(
            new EventRequest(
                "Concurrent registration",
                new AssignmentRequest.Config(6, null, null, null, null, null)));
    var executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        String name = "Concurrent attendee " + i;
        tasks.add(
            () -> {
              participants.create(
                  event.id(), new AssignmentRequest.ParticipantInput(name, 18, Gender.MALE, null));
              assignments.assign(event.id(), new EventAssignmentRequest(List.of(), null));
              return null;
            });
      }
      for (var result : executor.invokeAll(tasks)) {
        result.get();
      }
      assertEquals(20, participants.list(event.id()).size());
      var latest = assignments.latest(event.id()).orElseThrow();
      assertEquals(20, latest.result().totalAssignedParticipants());
      assertFalse(latest.result().blockers().isEmpty());
    } finally {
      executor.shutdownNow();
      events.delete(event.id());
    }
    assertTrue(
        repository.findAllByEventIdOrderByNameAscIdAsc(event.id()).isEmpty(),
        "Event deletion cascades to its roster");
  }
}
