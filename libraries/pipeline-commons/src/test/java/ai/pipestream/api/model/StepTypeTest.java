package ai.pipestream.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit tests for StepType enum using Quarkus-injected ObjectMapper.
 */
@QuarkusTest
public class StepTypeTest extends StepTypeTestBase {

    @Inject
    ObjectMapper objectMapper;
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}