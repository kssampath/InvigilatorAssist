package com.invigilatorassist.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public health check used by people, load balancers and the CI/CD smoke test.
 *
 * <p>{@code GET /api/health} reports the build version (the image tag set by Jenkins), the pod that answered and
 * whether PostgreSQL is reachable. It answers 200 when everything is up and 503 when the database is not.
 * Kubernetes probes use Actuator's {@code /actuator/health/liveness|readiness} instead.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final Clock clock;
    private final String applicationName;
    private final String version;
    private final String instance;

    public HealthController(DataSource dataSource,
                            Clock clock,
                            @Value("${spring.application.name:invigilator-api}") String applicationName,
                            @Value("${APP_VERSION:local}") String version,
                            @Value("${POD_NAME:${HOSTNAME:localhost}}") String instance) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.applicationName = applicationName;
        this.version = version;
        this.instance = instance;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = databaseUp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("service", applicationName);
        body.put("version", version);
        body.put("instance", instance);
        body.put("database", dbUp ? "UP" : "DOWN");
        body.put("timestamp", Instant.now(clock).toString());
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** Landing response for the bare API URL, so http://localhost:8080 shows something useful. */
    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", applicationName);
        body.put("version", version);
        body.put("health", "/api/health");
        body.put("docs", "/swagger-ui.html");
        return body;
    }

    private boolean databaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
