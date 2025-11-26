package ai.pipestream.api.annotation;

import jakarta.enterprise.util.Nonbinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufIncoming {

    /**
     * The channel name (e.g., "orders-in").
     */
    String value();

    /**
     * Pass-through configuration for Kafka/Apicurio.
     * Format: "key=value"
     * Example: @ProtobufIncoming(value="x", properties={"fetch.min.bytes=500",
     * "apicurio.registry.find-latest=false"})
     */
    @Nonbinding
    String[] properties() default {};
}
