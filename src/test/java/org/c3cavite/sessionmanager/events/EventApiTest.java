package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventApiTest {
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;

  @Test
  void createsReadsUpdatesListsAndDeletesAnEventWithResolvedConfig() throws Exception {
    var created =
        request(
            "POST",
            "/api/v1/events",
            """
        {"name":"Sunday session","config":{"capSize":6}}
        """);
    assertEquals(201, created.statusCode(), created.body());
    var event = json.readTree(created.body());
    String path = "/api/v1/events/" + event.path("id").asString();
    assertEquals(path, created.headers().firstValue("Location").orElseThrow());
    assertEquals(4, event.path("config").path("targetSize").asInt());
    assertEquals("SEPARATED", event.path("config").path("genderMode").asString());
    assertEquals(3, event.path("config").path("activeTiers").size());
    assertTrue(event.path("config").path("lifeStageSubgroupingEnabled").asBoolean());
    assertEquals(
        23,
        event.path("config").path("ageTierBoundaries").path("YOUNG_ADULT").path("minAge").asInt());
    var read = request("GET", path, null);
    assertEquals(200, read.statusCode());
    assertEquals(event, json.readTree(read.body()));
    var updated =
        request(
            "PUT",
            path,
            """
        {"name":"Updated session","config":{"capSize":8,"targetSize":5,"genderMode":"MIXED"}}
        """);
    assertEquals(200, updated.statusCode(), updated.body());
    assertEquals("Updated session", json.readTree(updated.body()).path("name").asString());
    assertEquals(8, json.readTree(updated.body()).path("config").path("capSize").asInt());
    var listed = request("GET", "/api/v1/events", null);
    assertEquals(200, listed.statusCode());
    assertTrue(listed.body().contains(event.path("id").asString()));
    assertEquals(204, request("DELETE", path, null).statusCode());
    assertEquals(404, request("GET", path, null).statusCode());
    assertEquals(404, request("DELETE", path, null).statusCode());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"name\":\"\",\"config\":{\"capSize\":6}}",
        "{\"name\":\"Event\",\"config\":{}}",
        "{\"name\":\"Event\",\"config\":{\"capSize\":2}}",
        "{\"name\":\"Event\",\"config\":{\"capSize\":6,\"targetSize\":0}}"
      })
  void rejectsInvalidEventDataWithoutDatabaseErrors(String body) throws Exception {
    var response = request("POST", "/api/v1/events", body);
    assertEquals(400, response.statusCode(), response.body());
    assertTrue(json.readTree(response.body()).path("message").isString());
  }

  @Test
  void unknownAndMalformedEventIdsHaveCleanErrors() throws Exception {
    assertEquals(404, request("GET", "/api/v1/events/" + UUID.randomUUID(), null).statusCode());
    assertEquals(400, request("GET", "/api/v1/events/not-a-uuid", null).statusCode());
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
