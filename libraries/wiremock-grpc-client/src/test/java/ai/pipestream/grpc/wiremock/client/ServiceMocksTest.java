package ai.pipestream.grpc.wiremock.client;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(WireMockServerTestResource.class)
public class ServiceMocksTest {

    @Test
    void testServiceMocksFactory() {
        ServiceMocks mocks = new ServiceMocks();
        
        assertThat(mocks.accountManager(), is(notNullValue()));
        assertThat(mocks.connector(), is(notNullValue()));
        assertThat(mocks.platformRegistration(), is(notNullValue()));
        assertThat(mocks.mapping(), is(notNullValue()));
        assertThat(mocks.engine(), is(notNullValue()));
        assertThat(mocks.designMode(), is(notNullValue()));
        
        // Verify reset works (should return same instance for chaining)
        assertThat(mocks.reset(), is(sameInstance(mocks)));
        
        // Verify setupDefaults works
        assertThat(mocks.setupDefaults(), is(sameInstance(mocks)));
    }
}