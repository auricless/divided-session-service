package org.c3cavite.sessionmanager.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.sift.MDCBasedDiscriminator;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoggingSecurityTest {
  @ParameterizedTest
  @ValueSource(strings = {"../outside", "..", "${user.home}", "/tmp/outside", "..\\outside"})
  void rejectsMdcValuesThatCouldEscapeALogDirectory(String unsafeValue) {
    assertEquals("default", discriminate(unsafeValue));
  }

  @ParameterizedTest
  @ValueSource(strings = {"session-1", "group_2"})
  void preservesSafeLogFileSegments(String safeValue) {
    assertEquals(safeValue, discriminate(safeValue));
  }

  private String discriminate(String value) {
    var discriminator = new MDCBasedDiscriminator();
    discriminator.setKey("session");
    discriminator.setDefaultValue("default");
    discriminator.start();
    var event = new LoggingEvent();
    event.setMDCPropertyMap(Map.of("session", value));
    return discriminator.getDiscriminatingValue(event);
  }
}
