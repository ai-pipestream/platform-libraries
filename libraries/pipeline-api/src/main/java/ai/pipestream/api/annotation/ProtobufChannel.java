package ai.pipestream.api.annotation;

import jakarta.enterprise.util.Nonbinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufChannel {

    /**
     * The channel name (e.g., "orders-out").
     */
    String value();

    /**
     * Pass-through configuration.
     * Example: @ProtobufChannel(value="x", properties={"acks=all"})
     */
    @Nonbinding
    String[] properties() default {};
}
