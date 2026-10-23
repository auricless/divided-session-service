package org.c3cavite.sessionmanager.events;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.h2.tools.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@DirtiesContext
class ReadinessApiTest {
  private static Server database;
  private static int databasePort;
  @LocalServerPort private int port;

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry properties) throws Exception {
    database = startDatabase(0);
    databasePort = database.getPort();
    properties.add(
        "spring.datasource.url",
        () ->
            "jdbc:h2:tcp://127.0.0.1:"
                + databasePort
                + "/mem:readiness;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    properties.add("spring.datasource.username", () -> "sa");
    properties.add("spring.datasource.password", () -> "");
    properties.add("spring.datasource.hikari.connection-timeout", () -> "250");
    properties.add("spring.datasource.hikari.validation-timeout", () -> "250");
  }

  @AfterAll
  static void stopDatabase() {
    if (database != null) {
      database.stop();
    }
  }

  @Test
  void healthOnlyRuntimeDoesNotIncludeUnpatchedHistogramMetrics() {
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("org.HdrHistogram.DoubleHistogram"));
  }

  @Test
  void reportsReadyThenUnavailableWhenItsDatabaseStops() throws Exception {
    var ready = get("readiness");
    assertEquals(200, ready.statusCode(), ready.body());
    assertEquals("{\"status\":\"UP\"}", ready.body());
    database.stop();
    var unavailable = get("readiness");
    assertEquals(503, unavailable.statusCode(), unavailable.body());
    assertEquals("{\"status\":\"DOWN\"}", unavailable.body());
    assertEquals(200, get("liveness").statusCode());
  }

  private static Server startDatabase(int port) throws Exception {
    return Server.createTcpServer("-tcpPort", Integer.toString(port), "-tcpDaemon", "-ifNotExists")
        .start();
  }

  private HttpResponse<String> get(String group) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/actuator/health/" + group))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
