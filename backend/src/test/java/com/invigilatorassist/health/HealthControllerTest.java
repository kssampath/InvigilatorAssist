package com.invigilatorassist.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void reportsUpWithVersionWhenDatabaseIsReachable() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response =
                new HealthController(ds, clock, "invigilator-api", "42-abc123", "api-pod-1").health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals("UP", body.get("status"));
        assertEquals("UP", body.get("database"));
        assertEquals("42-abc123", body.get("version"));
        assertEquals("api-pod-1", body.get("instance"));
        assertEquals("2026-11-23T10:00:00Z", body.get("timestamp"));
    }

    @Test
    void reportsDownWith503WhenDatabaseIsUnreachable() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));

        ResponseEntity<Map<String, Object>> response =
                new HealthController(ds, clock, "invigilator-api", "local", "localhost").health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
    }

    @Test
    void rootPointsToHealthAndDocs() {
        Map<String, Object> body =
                new HealthController(mock(DataSource.class), clock, "invigilator-api", "local", "localhost").root();
        assertEquals("/api/health", body.get("health"));
        assertEquals("/swagger-ui.html", body.get("docs"));
    }
}
