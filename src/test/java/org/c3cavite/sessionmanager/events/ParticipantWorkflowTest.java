package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParticipantWorkflowTest {
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;

  @Test
  void registersEditsAndRemovesAttendeesOnlyWithinTheirEvent() throws Exception {
    String event = createEvent();
    String other = createEvent();
    try {
      var created =
          request(
              "POST",
              event + "/participants",
              "{\"name\":\"Alex\",\"age\":18,\"gender\":\"MALE\"}");
      assertEquals(201, created.statusCode(), created.body());
      String participant = created.headers().firstValue("Location").orElseThrow();
      assertEquals(200, request("GET", participant, null).statusCode());
      assertEquals(1, body(request("GET", event + "/participants", null)).size());
      String foreign = other + "/participants/" + body(created).path("id").asString();
      assertEquals(404, request("GET", foreign, null).statusCode());
      assertEquals(404, request("DELETE", foreign, null).statusCode());
      assertEquals(
          200,
          request(
                  "PUT",
                  participant,
                  "{\"name\":\"Alex updated\",\"age\":24,\"gender\":\"MALE\",\"lifeStage\":\"STUDENT\"}")
              .statusCode());
      assertEquals(
          "YOUNG_ADULT", body(request("GET", participant, null)).path("ageTier").asString());
      assertEquals(204, request("DELETE", participant, null).statusCode());
      assertEquals(404, request("GET", participant, null).statusCode());
      assertTrue(body(request("GET", event + "/participants", null)).isEmpty());
    } finally {
      request("DELETE", event, null);
      request("DELETE", other, null);
    }
  }

  @Test
  void validatesParticipantDataAgainstTheStoredEventConfiguration() throws Exception {
    String event = createEvent();
    try {
      for (String invalid :
          new String[] {
            "{}",
            "{\"name\":\"A\",\"age\":24,\"gender\":\"MALE\"}",
            "{\"name\":\"A\",\"age\":0,\"gender\":\"MALE\"}"
          }) {
        assertEquals(400, request("POST", event + "/participants", invalid).statusCode());
      }
      assertTrue(body(request("GET", event + "/participants", null)).isEmpty());
    } finally {
      request("DELETE", event, null);
    }
  }

  @Test
  void computesStoredRosterAndInvalidatesTheSnapshotAfterRosterOrConfigChanges() throws Exception {
    String event = createEvent();
    try {
      assertEquals(204, request("GET", event + "/assignment", null).statusCode());
      var p =
          request(
              "POST",
              event + "/participants",
              "{\"name\":\"Alex\",\"age\":18,\"gender\":\"MALE\"}");
      assertEquals(201, p.statusCode());
      String roster =
          """
          {"facilitators":[{"name":"Sam","role":"LEADER","gender":"MALE","qualifiedTiers":["YOUTH"]}]}
          """;
      var computed = request("POST", event + "/assignment", roster);
      assertEquals(200, computed.statusCode(), computed.body());
      assertEquals("FINAL", body(computed).path("result").path("state").asString());
      assertEquals(1, body(computed).path("result").path("totalAssignedParticipants").asInt());
      assertEquals(body(computed), body(request("GET", event + "/assignment", null)));
      assertEquals(
          200,
          request(
                  "PUT",
                  p.headers().firstValue("Location").orElseThrow(),
                  "{\"name\":\"Alex updated\",\"age\":18,\"gender\":\"MALE\"}")
              .statusCode());
      assertEquals(204, request("GET", event + "/assignment", null).statusCode());
      assertEquals(200, request("POST", event + "/assignment", roster).statusCode());
      assertEquals(
          200,
          request("PUT", event, "{\"name\":\"Changed\",\"config\":{\"capSize\":8}}").statusCode());
      assertEquals(204, request("GET", event + "/assignment", null).statusCode());
      var proposal = request("POST", event + "/assignment", "{\"facilitators\":[]}");
      assertEquals("NON_FINAL_PROPOSAL", body(proposal).path("result").path("state").asString());
      assertFalse(body(proposal).path("result").path("blockers").isEmpty());
    } finally {
      request("DELETE", event, null);
    }
  }

  @Test
  void explicitFacilitatorExceptionsAreVisibleAndWithdrawnOnTheNextAssignment() throws Exception {
    String event = createEvent();
    try {
      request(
          "POST", event + "/participants", "{\"name\":\"Alex\",\"age\":18,\"gender\":\"MALE\"}");
      String roster =
          "\"facilitators\":[{\"name\":\"Sam\",\"role\":\"LEADER\",\"gender\":\"FEMALE\",\"qualifiedTiers\":[]}]";
      var allowed =
          request(
              "POST",
              event + "/assignment",
              "{"
                  + roster
                  + ",\"facilitatorExceptions\":[{\"facilitatorIndex\":0,\"allowAgeTierMismatch\":true,\"allowGenderMismatch\":true}]}");
      assertEquals(200, allowed.statusCode(), allowed.body());
      assertEquals("FINAL", body(allowed).path("result").path("state").asString());
      assertFalse(body(allowed).path("result").path("warnings").isEmpty());
      var strict = request("POST", event + "/assignment", "{" + roster + "}");
      assertEquals("NON_FINAL_PROPOSAL", body(strict).path("result").path("state").asString());
      assertEquals(body(strict), body(request("GET", event + "/assignment", null)));
    } finally {
      request("DELETE", event, null);
    }
  }

  @Test
  void creatingAndDeletingParticipantsInvalidatesThePreviousSnapshot() throws Exception {
    String event = createEvent();
    try {
      assertEquals(
          "FINAL",
          body(request("POST", event + "/assignment", "{\"facilitators\":[]}"))
              .path("result")
              .path("state")
              .asString());
      var created =
          request(
              "POST",
              event + "/participants",
              "{\"name\":\"Alex\",\"age\":18,\"gender\":\"MALE\"}");
      assertEquals(204, request("GET", event + "/assignment", null).statusCode());
      request("POST", event + "/assignment", "{\"facilitators\":[]}");
      assertEquals(
          204,
          request("DELETE", created.headers().firstValue("Location").orElseThrow(), null)
              .statusCode());
      assertEquals(204, request("GET", event + "/assignment", null).statusCode());
    } finally {
      request("DELETE", event, null);
    }
  }

  @Test
  void invalidNewBoundariesDoNotCommitAnIncompatibleParticipantConfiguration() throws Exception {
    String event = createEvent();
    try {
      request(
          "POST", event + "/participants", "{\"name\":\"Alex\",\"age\":18,\"gender\":\"MALE\"}");
      var original = body(request("GET", event, null));
      var invalid =
          request(
              "PUT",
              event,
              """
          {"name":"Changed","config":{"capSize":6,"ageTierBoundaries":{
          "TEENS":{"minAge":13,"maxAge":14},"YOUTH":{"minAge":15,"maxAge":17},
          "YOUNG_ADULT":{"minAge":18,"maxAge":2147483647}}}}
          """);
      assertEquals(400, invalid.statusCode(), invalid.body());
      assertEquals(original, body(request("GET", event, null)));
    } finally {
      request("DELETE", event, null);
    }
  }

  @Test
  void renamingAnEventPreservesTheStillValidAssignment() throws Exception {
    String event = createEvent();
    try {
      var computed = body(request("POST", event + "/assignment", "{\"facilitators\":[]}"));
      assertEquals(
          200,
          request("PUT", event, "{\"name\":\"Renamed\",\"config\":{\"capSize\":6}}").statusCode());
      var latest = request("GET", event + "/assignment", null);
      assertEquals(200, latest.statusCode(), latest.body());
      assertEquals(computed.path("result"), body(latest).path("result"));
      assertTrue(body(latest).path("revision").asLong() > computed.path("revision").asLong());
    } finally {
      request("DELETE", event, null);
    }
  }

  private String createEvent() throws Exception {
    var response =
        request("POST", "/api/v1/events", "{\"name\":\"Workflow\",\"config\":{\"capSize\":6}}");
    assertEquals(201, response.statusCode(), response.body());
    return response.headers().firstValue("Location").orElseThrow();
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private HttpResponse<String> request(String method, String path, String body) throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
