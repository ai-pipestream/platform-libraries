package ai.pipestream.dynamic.grpc;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class RandomPortProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.grpc.server.port", "0");
    }
}
