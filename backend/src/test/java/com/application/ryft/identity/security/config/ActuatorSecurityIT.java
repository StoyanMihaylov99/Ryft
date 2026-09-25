package com.application.ryft.identity.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.application.ryft.AbstractIntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the split described in {@link SecurityConfig}'s javadoc actually holds at runtime, on both
 * real ports: the separate management port ({@code management.server.port}, see application.yaml) is
 * where Prometheus/container probes reach {@code health} and {@code prometheus} with no JWT, while
 * the public API port serves neither unauthenticated — not because of a path-based rule, but because
 * {@link EndpointRequest#toAnyEndpoint()} only matches a request against whichever context's own
 * {@code PathMappedEndpoints} actually has something mapped. {@code MockMvc} exercises the public API
 * port's own {@code DispatcherServlet}/security chain in-process; a plain JDK {@link HttpClient} plus
 * {@link LocalManagementPort} makes a real HTTP call against the separate embedded server the
 * management context starts on its own port, since MockMvc has no way to reach a second embedded
 * server (and this project doesn't otherwise depend on {@code TestRestTemplate}'s module).
 */
@AutoConfigureMockMvc
// A dynamic management port, not the fixed 8081 from application.yaml — avoids colliding with a
// real dev instance already running locally on 8081, or with another test JVM in CI.
@TestPropertySource(properties = "management.server.port=0")
class ActuatorSecurityIT extends AbstractIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MockMvc mockMvc;

    private HttpResponse<String> getFromManagementPort(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsPublicOnTheManagementPort() throws Exception {
        HttpResponse<String> response = getFromManagementPort("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessAndReadinessProbesArePublicOnTheManagementPort() throws Exception {
        assertThat(getFromManagementPort("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(getFromManagementPort("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void prometheusScrapeWorksWithoutAJwtOnTheManagementPortAndExposesExpectedMetrics() throws Exception {
        // Actuator's own endpoints are deliberately excluded from the http.server.requests timer (so
        // probe/scrape traffic doesn't skew the real API's latency histogram) — generate at least one
        // sample against a real API route first, so the bucket series below is guaranteed to exist
        // regardless of which order JUnit happens to run this class's test methods in.
        mockMvc.perform(get("/api/v1/projects"));

        HttpResponse<String> response = getFromManagementPort("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("http_server_requests_seconds_bucket");
        assertThat(body).contains("jvm_memory_used_bytes");
        assertThat(body).contains("hikaricp_connections");
    }

    @Test
    void publicApiPortDoesNotExposeHealthOrPrometheusUnauthenticated() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicApiPortStillRequiresAuthenticationForRealEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }
}
