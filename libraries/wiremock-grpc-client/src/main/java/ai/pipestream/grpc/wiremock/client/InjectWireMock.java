package ai.pipestream.grpc.wiremock.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject the WireMock server into test fields.
 * <p>
 * When used with WireMockServerTestResource, this can inject the WireMockServer instance
 * (if available) or other helper objects.
 * <p>
 * Note: In the Docker-based setup, we might not have direct access to the server object,
 * but we can inject the client or port.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectWireMock {
}