package ai.pipestream.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Unit tests for PipelineGraphConfig using Quarkus-injected ObjectMapper.
 * All test logic is in PipelineGraphConfigTestBase.
 */
@QuarkusTest
public class PipelineGraphConfigTest extends PipelineGraphConfigTestBase {

    @Inject
    ObjectMapper objectMapper;
    
    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}