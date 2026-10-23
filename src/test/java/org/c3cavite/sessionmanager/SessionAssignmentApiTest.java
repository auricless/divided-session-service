package org.c3cavite.sessionmanager;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionAssignmentApiTest {
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private static final String PARTICIPANT =
      """
            {"name":"Alex","age":18,"gender":"MALE"}
            """;
  private static final String LEADER =
      """
            {"name":"Sam","role":"LEADER","gender":"MALE","qualifiedTiers":["YOUTH"]}
            """;

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;

  @Test
  void returnsAStaffedFinalAssignmentUsingCoreDefaults() throws Exception {
    JsonNode result = success(request("{\"capSize\":6}", PARTICIPANT, LEADER, ""));

    assertEquals("FINAL", result.path("state").asString());
    assertEquals(1, result.path("totalGroups").asInt());
    assertEquals(1, result.path("totalAssignedParticipants").asInt());
    assertTrue(result.path("blockers").isEmpty());
    JsonNode group = result.path("groups").get(0);
    assertEquals("YOUTH", group.path("ageTierTarget").asString());
    assertEquals("MALE", group.path("genderTarget").asString());
    assertEquals("Sam", group.path("leader").path("name").asString());
    assertEquals("YOUTH", group.path("participants").get(0).path("ageTier").asString());
    assertEquals(1, group.path("size").asInt());
  }

  @Test
  void returnsAnActionableProposalInsteadOfFinalizingALeaderlessGroup() throws Exception {
    JsonNode result = success(request("{\"capSize\":6}", PARTICIPANT, "", ""));

    assertEquals("NON_FINAL_PROPOSAL", result.path("state").asString());
    JsonNode blocker = result.path("blockers").get(0);
    assertEquals("MISSING_LEADER", blocker.path("code").asString());
    assertEquals(1, blocker.path("groupNumber").asInt());
    assertTrue(blocker.path("message").asString().contains("LEADER"));
    assertTrue(result.path("groups").get(0).path("leader").isNull());
  }

  @Test
  void appliesExplicitExceptionsToTheRosterInstanceForOnlyOneRun() throws Exception {
    String mismatchedLeader = LEADER.replace("MALE", "FEMALE").replace("YOUTH", "TEENS");
    String exceptions =
        """
                ,"facilitatorExceptions":[{"facilitatorIndex":0,"allowAgeTierMismatch":true,"allowGenderMismatch":true}]
                """;
    JsonNode allowed =
        success(request("{\"capSize\":6}", PARTICIPANT, mismatchedLeader, exceptions));
    assertEquals("FINAL", allowed.path("state").asString());
    assertFalse(allowed.path("warnings").isEmpty());

    JsonNode strict = success(request("{\"capSize\":6}", PARTICIPANT, mismatchedLeader, ""));
    assertEquals("NON_FINAL_PROPOSAL", strict.path("state").asString());
  }

  @Test
  void emptyQualificationsRequireAnExplicitExceptionForThisRequestOnly() throws Exception {
    String unqualifiedLeader = LEADER.replace("[\"YOUTH\"]", "[]");
    String strictRequest = request("{\"capSize\":6}", PARTICIPANT, unqualifiedLeader, "");
    assertEquals("NON_FINAL_PROPOSAL", success(strictRequest).path("state").asString());

    JsonNode allowed =
        success(
            request(
                "{\"capSize\":6}",
                PARTICIPANT,
                unqualifiedLeader,
                """
                ,"facilitatorExceptions":[{"facilitatorIndex":0,"allowAgeTierMismatch":true}]
                """));
    assertEquals("FINAL", allowed.path("state").asString());
    assertTrue(allowed.path("groups").get(0).path("leader").path("qualifiedTiers").isEmpty());
    assertFalse(allowed.path("warnings").isEmpty());
    assertEquals("NON_FINAL_PROPOSAL", success(strictRequest).path("state").asString());
  }

  @Test
  void doesNotPromoteAnAssistantWhenAnExceptionIsGranted() throws Exception {
    JsonNode result =
        success(
            request(
                "{\"capSize\":6}",
                PARTICIPANT,
                LEADER.replace("LEADER", "ASSISTANT"),
                """
                ,"facilitatorExceptions":[{"facilitatorIndex":0,"allowGenderMismatch":true}]
                """));
    assertEquals("NON_FINAL_PROPOSAL", result.path("state").asString());
    assertTrue(result.path("groups").get(0).path("leader").isNull());
    assertEquals(
        "ASSISTANT", result.path("groups").get(0).path("assistant").path("role").asString());
  }

  @Test
  void mapsCustomBoundariesAndAllOptionalConfiguration() throws Exception {
    String config =
        """
                {"capSize":6,"targetSize":2,"genderMode":"MIXED","lifeStageSubgroupingEnabled":false,
                 "activeTiers":["TEENS"],"ageTierBoundaries":{
                  "TEENS":{"minAge":13,"maxAge":19},"YOUTH":{"minAge":20,"maxAge":25},
                  "YOUNG_ADULT":{"minAge":26,"maxAge":100}}}
                """;
    String attendees =
        PARTICIPANT + "," + PARTICIPANT.replace("Alex", "Bea").replace("MALE", "FEMALE");
    JsonNode result = success(request(config, attendees, LEADER.replace("YOUTH", "TEENS"), ""));
    JsonNode group = result.path("groups").get(0);
    assertEquals("FINAL", result.path("state").asString());
    assertEquals(2, group.path("size").asInt());
    assertTrue(group.path("genderTarget").isNull());
    assertEquals("TEENS", group.path("participants").get(0).path("ageTier").asString());
  }

  @Test
  void preservesYoungAdultLifeStagesAndHardCaps() throws Exception {
    String student = PARTICIPANT.replace("18", "25").replace("}", ",\"lifeStage\":\"STUDENT\"}");
    String worker = student.replace("Alex", "Pat").replace("STUDENT", "WORKING_PROFESSIONAL");
    JsonNode result = success(request("{\"capSize\":4}", student + "," + worker, "", ""));
    assertEquals(2, result.path("totalGroups").asInt());
    assertEquals("STUDENT", result.path("groups").get(0).path("lifeStageTarget").asString());
    assertEquals(
        "WORKING_PROFESSIONAL", result.path("groups").get(1).path("lifeStageTarget").asString());
    assertTrue(result.path("blockers").toString().contains("MINIMUM_GROUP_SIZE"));

    String five = String.join(",", java.util.Collections.nCopies(5, PARTICIPANT));
    JsonNode capped = success(request("{\"capSize\":2,\"targetSize\":2}", five, "", ""));
    assertEquals(5, capped.path("totalAssignedParticipants").asInt());
    for (JsonNode group : capped.path("groups")) {
      assertTrue(group.path("size").asInt() <= 2);
    }
  }

  @Test
  void returnsUnassignedAttendeesAndWarningsWithoutLosingTheirDetails() throws Exception {
    String attendees = PARTICIPANT.replace("18", "12") + "," + PARTICIPANT;
    JsonNode result =
        success(request("{\"capSize\":6,\"activeTiers\":[\"TEENS\"]}", attendees, "", ""));
    assertEquals("FINAL", result.path("state").asString());
    assertEquals(0, result.path("totalAssignedParticipants").asInt());
    assertEquals(2, result.path("unassignedParticipants").size());
    assertEquals(
        12, result.path("unassignedParticipants").get(0).path("participant").path("age").asInt());
    assertFalse(result.path("unassignedParticipants").get(0).path("reason").asString().isBlank());
    assertTrue(result.path("warnings").toString().contains("under 13"));
  }

  @Test
  void acceptsEmptyRostersAsAnEmptyFinalResult() throws Exception {
    JsonNode result = success(request("{\"capSize\":6}", "", "", ""));
    assertEquals("FINAL", result.path("state").asString());
    assertTrue(result.path("groups").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "null",
        "{\"config\":{},\"participants\":[],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6},\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6},\"participants\":[]}",
        "{\"config\":{\"capSize\":0},\"participants\":[],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6,\"activeTiers\":[]},\"participants\":[],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6},\"participants\":[null],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6},\"participants\":[],\"facilitators\":[null]}",
        "{\"config\":{\"capSize\":6},\"participants\":[],\"facilitators\":[],\"facilitatorExceptions\":[null]}",
        "{\"config\":{\"capSize\":6},\"participants\":[],\"facilitators\":[],\"facilitatorExceptions\":[{\"facilitatorIndex\":0}]}",
        "{\"config\":{\"capSize\":6},\"participants\":[],\"facilitators\":[],\"facilitatorExceptions\":[{}]}",
        "{\"config\":{\"capSize\":6},\"participants\":[{\"name\":\"Alex\",\"age\":25,\"gender\":\"MALE\"}],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6,\"genderMode\":\"UNKNOWN\"},\"participants\":[],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":6,\"ageTierBoundaries\":{\"TEENS\":null}},\"participants\":[],\"facilitators\":[]}",
        "{\"config\":{\"capSize\":\"invalid\"},\"participants\":[],\"facilitators\":[]}",
        "{"
      })
  void rejectsInvalidRequestsWithACleanClientError(String body) throws Exception {
    HttpResponse<String> response = post(body);
    assertEquals(400, response.statusCode(), response.body());
    JsonNode error = json.readTree(response.body());
    assertFalse(error.path("message").asString().isBlank());
    assertFalse(error.has("trace"));
    assertFalse(response.body().contains("Exception"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"capSize\":6.9}",
        "{\"capSize\":\"6\"}",
        "{\"capSize\":6,\"genderMode\":0}",
        "{\"capSize\":6,\"lifeStageSubgroupingEnabled\":\"false\"}"
      })
  void rejectsConfigurationTypesThatWouldOtherwiseBeSilentlyCoerced(String config)
      throws Exception {
    assertEquals(400, post(request(config, "", "", "")).statusCode());
  }

  @Test
  void rejectsFractionalParticipantAgesInsteadOfRoundingIntoAnAgeTier() throws Exception {
    assertEquals(
        400,
        post(request("{\"capSize\":6}", PARTICIPANT.replace("18", "18.9"), "", "")).statusCode());
  }

  @Test
  void requiresAnActualBooleanForAnExplicitExceptionFlag() throws Exception {
    assertEquals(
        400,
        post(request(
                "{\"capSize\":6}",
                PARTICIPANT,
                LEADER,
                """
                ,"facilitatorExceptions":[{"facilitatorIndex":0,"allowGenderMismatch":"true"}]
                """))
            .statusCode());
  }

  private String request(String config, String participants, String facilitators, String extra) {
    return "{\"config\":"
        + config
        + ",\"participants\":["
        + participants
        + "],\"facilitators\":["
        + facilitators
        + "]"
        + extra
        + "}";
  }

  private JsonNode success(String body) throws Exception {
    HttpResponse<String> response = post(body);
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(
        response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    return json.readTree(response.body());
  }

  private HttpResponse<String> post(String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/sessions/assign"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
