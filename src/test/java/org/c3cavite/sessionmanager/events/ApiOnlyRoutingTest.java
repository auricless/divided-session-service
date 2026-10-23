package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.c3cavite.sessionmanager.api.AssignmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiOnlyRoutingTest {
  @LocalServerPort private int port;
  @Autowired private EventService events;

  @Test
  void servesEventJsonButLeavesReactRoutesAndAssetsToTheIndependentUiServer() throws Exception {
    var event =
        events.create(
            new EventRequest(
                "<script>Event</script>",
                new AssignmentRequest.Config(6, null, null, null, null, null)));
    try {
      var json = get("/api/v1/events/" + event.id());
      assertEquals(200, json.statusCode());
      assertTrue(
          json.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
      assertEquals(404, get("/events").statusCode());
      assertEquals(404, get("/events/" + event.id()).statusCode());
      assertEquals(404, get("/events/" + event.id() + "/groups").statusCode());
      assertEquals(404, get("/assets/registration.js").statusCode());
    } finally {
      events.delete(event.id());
    }
  }

  @Test
  void retainsApiSecurityAndCacheHeadersAndDoesNotFallbackToHtmlForMissingApiRoutes()
      throws Exception {
    var response = get("/api/v1/events");
    assertEquals(200, response.statusCode());
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    assertTrue(
        response
            .headers()
            .firstValue("Content-Security-Policy")
            .orElseThrow()
            .contains("script-src 'self'"));
    assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
    var missing = get("/api/v1/not-a-route");
    assertEquals(404, missing.statusCode());
    assertFalse(missing.body().contains("<div id=\"root\""));
  }

  private HttpResponse<String> get(String path) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
