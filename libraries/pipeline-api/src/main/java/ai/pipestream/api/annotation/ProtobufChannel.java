package ai.pipestream.api.annotation;

import jakarta.enterprise.util.Nonbinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring Kafka message producers that send Protobuf messages.
 *
 * <p>This annotation works in conjunction with the Pipeline Protobuf Kafka Connector
 * extension to automatically configure Kafka producers for Protobuf message processing.
 * When applied to a field (typically an Emitter-like messaging type),
 * it triggers build-time configuration generation that sets up the appropriate serializers
 * and Apicurio Registry integration.</p>
 *
 * <h2>Usage</h2>
 * <p>Use this annotation on fields that emit messages to Kafka:</p>
 * <pre>{@code
 * @ApplicationScoped
 * public class OrderService {
 *
 *     @ProtobufChannel("orders-out")
 *     Emitter<OrderEvent> orderEmitter;
 *
 *     public void createOrder(OrderEvent order) {
 *         orderEmitter.send(order);
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
 *   <li>Key serializer: {@code UUIDSerializer}</li>
 *   <li>Value serializer: {@code ProtobufKafkaSerializer}</li>
 *   <li>Apicurio Registry settings for schema registration</li>
 * </ul>
 *
 * <h2>Configuration Override</h2>
 * <p>Additional Kafka properties can be specified using the {@link #properties()} attribute:</p>
 * <pre>{@code
 * @ProtobufChannel(
 *     value = "orders-out",
 *     properties = {
 *         "acks=all",
 *         "retries=3",
 *         "apicurio.registry.auto-register=true"
 *     }
 * )
 * Emitter<OrderEvent> orderEmitter;
 * }</pre>
 *
 * @see ProtobufIncoming
 * @since 0.2.10
 */
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufChannel {

    /**
     * The channel name for this producer.
     *
     * <p>This name is used to identify the messaging channel and generate
     * configuration properties. It typically follows a convention like
     * "topic-name-out" (e.g., "orders-out", "users-out").</p>
     *
     * <p>The channel name is used to derive the Kafka topic name by removing
     * common suffixes like "-in" or "-out". For example, "orders-out" becomes
     * the topic "orders".</p>
     *
     * @return The channel name for this producer
     */
    String value();

    /**
     * Additional Kafka configuration properties.
     *
     * <p>This attribute allows specifying additional Kafka producer properties
     * that will be applied to this channel. Each property should be in the
     * format "key=value".</p>
     *
     * <h3>Examples</h3>
     * <pre>{@code
     * // Producer reliability configuration
     * @ProtobufChannel(
     *     value = "orders-out",
     *     properties = {
     *         "acks=all",
     *         "retries=3",
     *         "max.in.flight.requests.per.connection=1"
     *     }
     * )
     *
     * // Apicurio Registry configuration
     * @ProtobufChannel(
     *     value = "orders-out",
     *     properties = {
     *         "apicurio.registry.auto-register=true",
     *         "apicurio.registry.find-latest=true"
     *     }
     * )
     * }</pre>
     *
     * @return Array of property strings in "key=value" format
     */
    @Nonbinding
    String[] properties() default {};
}
