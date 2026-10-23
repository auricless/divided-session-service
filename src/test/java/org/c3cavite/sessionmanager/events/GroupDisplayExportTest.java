package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.c3cavite.core.domain.Gender;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GroupDisplayExportTest {
  @LocalServerPort private int port;
  @Autowired private EventService events;
  @Autowired private ParticipantService participants;
  @Autowired private EventAssignmentService assignments;

  @Test
  void exportsProposalsWithEscapedSpreadsheetCells() throws Exception {
    var event =
        events.create(
            new EventRequest(
                "+Event", new AssignmentRequest.Config(6, null, null, null, null, null)));
    try {
      String export = "/api/v1/events/" + event.id() + "/assignment/export.csv";
      assertEquals(409, get(export).statusCode());
      participants.create(
          event.id(),
          new AssignmentRequest.ParticipantInput("=SUM(1,2)\n\"Ada\"", 18, Gender.MALE, null));
      assignments.assign(event.id(), new EventAssignmentRequest(List.of(), null));
      var csv = get(export);
      assertEquals(200, csv.statusCode(), csv.body());
      assertTrue(csv.headers().firstValue("Content-Type").orElseThrow().contains("text/csv"));
      assertTrue(
          csv.headers().firstValue("Content-Disposition").orElseThrow().contains("attachment"));
      assertTrue(csv.body().contains("NON_FINAL_PROPOSAL"));
      assertTrue(csv.body().contains("BLOCKER"));
      assertTrue(csv.body().contains("\"'+Event\""));
      assertTrue(csv.body().contains("\"'=SUM(1,2)\n\"\"Ada\"\"\""));
      assertTrue(csv.body().contains("WARNING"));
      participants.create(
          event.id(),
          new AssignmentRequest.ParticipantInput("New attendee", 18, Gender.MALE, null));
      assertEquals(409, get(export).statusCode(), "Never export an invalidated assignment");
    } finally {
      events.delete(event.id());
    }
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
