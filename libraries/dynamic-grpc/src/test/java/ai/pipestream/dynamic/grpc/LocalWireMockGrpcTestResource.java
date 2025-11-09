package ai.pipestream.dynamic.grpc;

import ai.pipestream.grpc.wiremock.InjectWireMock;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.wiremock.grpc.GrpcExtensionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Local test resource that starts WireMock with gRPC support and provisions
 * the services.dsc descriptor from the grpc-stubs classpath into a temporary
 * directory so WireMock can load it.
 */
public class LocalWireMockGrpcTestResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;
    private Path tempRoot;

    @Override
    public Map<String, String> start() {
        try {
            tempRoot = Files.createTempDirectory("wiremock-grpc");
            Path grpcDir = tempRoot.resolve("grpc");
            Files.createDirectories(grpcDir);

            // Extract services.dsc from classpath: META-INF/grpc/services.dsc
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/grpc/services.dsc")) {
                if (in == null) {
                    throw new IllegalStateException("Could not find META-INF/grpc/services.dsc on classpath");
                }
                Files.copy(in, grpcDir.resolve("services.dsc"));
            }

            wireMockServer = new WireMockServer(
                    WireMockConfiguration.wireMockConfig()
                            .dynamicPort()
                            .extensions(new GrpcExtensionFactory())
                            .withRootDirectory(tempRoot.toString())
            );
            wireMockServer.start();

            return Map.of(
                    "test.wiremock.port", String.valueOf(wireMockServer.port())
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to start LocalWireMockGrpcTestResource", e);
        }
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (tempRoot != null) {
            try {
                Files.walk(tempRoot)
                        .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(
                wireMockServer,
                new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class)
        );
    }
}
