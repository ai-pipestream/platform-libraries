package ai.pipestream.api.annotation;

import jakarta.enterprise.util.Nonbinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring Kafka message consumers that process Protobuf messages.
 *
 * <p>This annotation works in conjunction with the Pipeline Protobuf Kafka Connector
 * extension to automatically configure Kafka consumers for Protobuf message processing.
 * When applied to a method, it triggers build-time configuration generation that sets
 * up the appropriate deserializers, Apicurio Registry integration, and return class mapping.</p>
 *
 * <h2>Usage</h2>
 * <p>Use this annotation alongside {@link org.eclipse.microprofile.reactive.messaging.Incoming}
 * to declare message consumer methods:</p>
 * <pre>{@code
 * @ApplicationScoped
 * public class OrderProcessor {
 *
 *     @Incoming("orders-in")  // Standard SmallRye annotation for wiring
 *     @ProtobufIncoming("orders-in")  // Extension annotation for auto-configuration
 *     public void processOrder(OrderEvent order) {
 *         // Process the order
 *     }
 * }
 * }</pre>
 *
 * <h2>Automatic Configuration</h2>
 * <p>The Pipeline Protobuf Kafka Connector extension automatically generates the following
 * configuration properties at build time:</p>
 * <ul>
 *   <li>Kafka topic name (derived from channel or explicit)</li>
 *   <li>Connector type: {@code smallrye-kafka}</li>
 *   <li>Key deserializer: {@code UUIDDeserializer}</li>
 *   <li>Value deserializer: {@code ProtobufKafkaDeserializer}</li>
 *   <li>Return class: The method parameter type (e.g., {@code com.example.OrderEvent})</li>
 *   <li>Apicurio Registry settings for schema resolution</li>
 * </ul>
 *
 * <h2>Configuration Override</h2>
 * <p>Additional Kafka properties can be specified using the {@link #properties()} attribute:</p>
 * <pre>{@code
 * @ProtobufIncoming(
 *     value = "orders-in",
 *     properties = {
 *         "fetch.min.bytes=500",
 *         "apicurio.registry.find-latest=false"
 *     }
 * )
 * public void processOrder(OrderEvent order) { ... }
 * }</pre>
 *
 * @see org.eclipse.microprofile.reactive.messaging.Incoming
 * @see ProtobufChannel
 * @see ai.pipestream.kafka.connector.deployment.ProtobufKafkaProcessor
 * @since 0.2.10
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufIncoming {

    /**
     * The channel name for this consumer.
     *
     * <p>This name is used to identify the messaging channel and generate
     * configuration properties. It typically follows a convention like
     * "topic-name-in" (e.g., "orders-in", "users-in").</p>
     *
     * <p>The channel name is used to derive the Kafka topic name by removing
     * common suffixes like "-in" or "-out". For example, "orders-in" becomes
     * the topic "orders".</p>
     *
     * @return The channel name for this consumer
     */
    String value();

    /**
     * Additional Kafka configuration properties.
     *
     * <p>This attribute allows specifying additional Kafka consumer properties
     * that will be applied to this channel. Each property should be in the
     * format "key=value".</p>
     *
     * <h3>Examples</h3>
     * <pre>{@code
     * // Consumer configuration
     * @ProtobufIncoming(
     *     value = "orders-in",
     *     properties = {
     *         "fetch.min.bytes=500",
     *         "max.poll.records=100",
     *         "auto.offset.reset=earliest"
     *     }
     * )
     *
     * // Apicurio Registry configuration
     * @ProtobufIncoming(
     *     value = "orders-in",
     *     properties = {
     *         "apicurio.registry.find-latest=false",
     *         "apicurio.registry.auto-register=true"
     *     }
     * )
     * }</pre>
     *
     * @return Array of property strings in "key=value" format
     */
    @Nonbinding
    String[] properties() default {};
}
