package ai.pipestream.grpc.wiremock.client;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public class WireMockServerTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> wiremockServer;
    private static final int WIREMOCK_HTTP_PORT = 8080;
    private static final int WIREMOCK_STREAMING_PORT = 50052;
    private static final String WIREMOCK_IMAGE_NAME = "ai-pipestream/pipestream-wiremock-server:latest";

    @Override
    public Map<String, String> start() {
        // Start the WireMock server Docker container
        wiremockServer = new GenericContainer<>(WIREMOCK_IMAGE_NAME)
                .withExposedPorts(WIREMOCK_HTTP_PORT, WIREMOCK_STREAMING_PORT) // Expose streaming port
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("WireMockContainer")))
                .waitingFor(Wait.forHttp("/__admin/health").forPort(WIREMOCK_HTTP_PORT).withStartupTimeout(Duration.ofSeconds(120)));

        wiremockServer.start();

        // Provide the dynamically assigned HTTP port to Quarkus tests
        System.setProperty("wiremock.url", "http://localhost:" + wiremockServer.getMappedPort(WIREMOCK_HTTP_PORT));
        System.setProperty("wiremock.http.port", String.valueOf(wiremockServer.getMappedPort(WIREMOCK_HTTP_PORT)));
        System.setProperty("wiremock.streaming.port", String.valueOf(wiremockServer.getMappedPort(WIREMOCK_STREAMING_PORT)));

        return Collections.singletonMap("wiremock.url", "http://localhost:" + wiremockServer.getMappedPort(WIREMOCK_HTTP_PORT));
    }

    @Override
    public void stop() {
        if (wiremockServer != null) {
            wiremockServer.stop();
        }
        System.clearProperty("wiremock.url");
        System.clearProperty("wiremock.http.port");
        System.clearProperty("wiremock.streaming.port");
    }
}
