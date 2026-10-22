package com.zuunr.dcentb.actuator;

import com.zuunr.dcentb.Application;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises dcentb's actuator support end-to-end over a real embedded server and real HTTP, against
 * a real MongoDB (see public/dcentb/README.md, "Start MongoDB"). Boots via SpringApplicationBuilder
 * directly rather than any Spring Boot *test* module (@SpringBootTest/MockMvc/TestRestTemplate) -
 * several of those moved or were removed in this Boot version in ways plain exploration couldn't
 * pin down reliably; SpringApplicationBuilder and server.port=0's local.server.port property are
 * long-stable, non-test-module Boot behavior.
 *
 * <p>widgets-test.openapi.json is used purely to let Application boot (dcentb.openapi.file has no
 * default - see DcentbAutoConfiguration) - it has no x-dcentb.asyncProcessing section, so async
 * processing stays disabled for this test, which is fine: the enabled case is already covered by
 * unit tests with mocks (AsyncProcessingHealthIndicatorTest, AsyncMetricsSamplerTest). What only an
 * IT like this can prove is the real safe-default posture: what's actually reachable by default,
 * and that nothing leaks the connection string.
 */
class DcentbActuatorIT {

    private static final String CONFIGURED_CONNECTION_STRING = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext context;
    private static int port;

    @BeforeAll
    static void startApplication() {
        context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "dcentb.openapi.file=classpath:widgets-test.openapi.json",
                        "dcentb.mongodb.db=dcentbactuatorit")
                .run();
        port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthReturnsUpWhenMongoIsReachable() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    void readinessAndLivenessGroupsExistAndAreUp() throws Exception {
        HttpResponse<String> readiness = get("/actuator/health/readiness");
        HttpResponse<String> liveness = get("/actuator/health/liveness");

        assertEquals(200, readiness.statusCode());
        assertTrue(readiness.body().contains("\"status\":\"UP\""), readiness.body());
        assertEquals(200, liveness.statusCode());
        assertTrue(liveness.body().contains("\"status\":\"UP\""), liveness.body());
    }

    @Test
    void onlyHealthIsExposedByDefaultEverythingElseIs404() throws Exception {
        assertEquals(404, get("/actuator/env").statusCode());
        assertEquals(404, get("/actuator/metrics").statusCode());
        assertEquals(404, get("/actuator/beans").statusCode());
    }

    @Test
    void theRawConnectionStringNeverAppearsInAnyReachableActuatorResponse() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            String body = get(path).body();
            assertFalse(body.contains(CONFIGURED_CONNECTION_STRING), path + " leaked the connection string: " + body);
            assertFalse(body.contains("adminpassword"), path + " leaked the Mongo password: " + body);
        }
    }
}
