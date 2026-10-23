package org.c3cavite.sessionmanager.security;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.util.ByteStreamWriter;

class PersistenceDependencySecurityTest {
  @Test
  @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
  void excludingEveryAuthenticationMethodMustFailClosed() throws Exception {
    Properties properties = new Properties();
    properties.setProperty(
        "user", System.getenv().getOrDefault("TEST_DATABASE_USER", "session_manager"));
    properties.setProperty("password", System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
    properties.setProperty("requireAuth", "!password,!md5,!gss,!sspi,!scram-sha-256,!none");
    SQLException failure =
        assertThrows(
            SQLException.class,
            () -> {
              try (var ignored =
                  DriverManager.getConnection(System.getenv("TEST_DATABASE_URL"), properties)) {
                // The server must never be accepted when the caller excluded every authentication
                // method.
              }
            });
    assertEquals("08004", failure.getSQLState());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
  void shortBinaryWritesPadWithZerosInsteadOfEarlierStatementBytes() throws Exception {
    try (var connection =
        DriverManager.getConnection(
            System.getenv("TEST_DATABASE_URL"),
            System.getenv().getOrDefault("TEST_DATABASE_USER", "session_manager"),
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""))) {
      try (var statement = connection.createStatement()) {
        statement.execute(
            "select 'private earlier statement value that must never appear in binary padding'");
      }
      try (var statement = connection.prepareStatement("select ?::bytea")) {
        byte[] content = new byte[20 * 1024];
        Arrays.fill(content, (byte) 42);
        statement.setObject(
            1,
            new ByteStreamWriter() {
              @Override
              public int getLength() {
                return 30 * 1024;
              }

              @Override
              public void writeTo(ByteStreamTarget target) throws java.io.IOException {
                target.getOutputStream().write(content);
              }
            });
        try (var result = statement.executeQuery()) {
          assertTrue(result.next());
          byte[] expected = Arrays.copyOf(content, 30 * 1024);
          byte[] actual = result.getBytes(1);
          assertArrayEquals(
              expected, actual, "Padding after content must not reuse earlier connection messages");
        }
      }
    }
  }

  @Test
  void unusedVulnerableJaxbXmlFactoryIsAbsentFromTheRuntime() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("org.glassfish.jaxb.core.v2.util.XmlFactory"));
  }
}
