package ai.pipestream.kafka.connector.deployment;

import ai.pipestream.common.interceptor.ApicurioConfigurator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.SystemPropertyBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Quarkus build-time processor for the Pipeline Protobuf Kafka Connector extension.
 *
 * <p>This processor implements the "No-Cheating" architecture that automatically configures
 * Kafka channels for Protobuf message serialization/deserialization. Instead of requiring
 * manual configuration in application.properties, it scans the codebase at build time
 * to discover {@link ai.pipestream.api.annotation.ProtobufIncoming} and
 * {@link ai.pipestream.api.annotation.ProtobufChannel} annotations and generates
 * the appropriate SmallRye Reactive Messaging configuration.</p>
 *
 * <h2>Architecture Overview</h2>
 * <p>The processor operates in two phases:</p>
 * <ol>
 *   <li><b>Build-Time Discovery:</b> Uses Jandex to scan for Protobuf annotations and
 *       extract message types</li>
 *   <li><b>Configuration Generation:</b> Produces {@link SystemPropertyBuildItem}s that
 *       configure Kafka serializers, deserializers, and Apicurio Registry settings</li>
 * </ol>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Automatic Protobuf type detection from method parameters and field generics</li>
 *   <li>Topic name derivation from channel names (removes -in/-out suffixes)</li>
 *   <li>Global Apicurio Registry URL bridging</li>
 *   <li>Parent-first classloading configuration for Protobuf artifacts</li>
 *   <li>Annotation-based property overrides</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This processor is designed to be thread-safe as it only reads from the Jandex index
 * and produces build items. No mutable state is maintained between build steps.</p>
 *
 * @see ai.pipestream.api.annotation.ProtobufIncoming
 * @see ai.pipestream.api.annotation.ProtobufChannel
 * @see ai.pipestream.common.interceptor.ApicurioConfigurator
 * @since 0.2.10
 */
public class ProtobufKafkaProcessor {

    /** Logger for build-time operations and configuration generation. */
    private static final Logger LOG = Logger.getLogger(ProtobufKafkaProcessor.class);

    /** DotName for the {@link ai.pipestream.api.annotation.ProtobufIncoming} annotation used on consumer methods. */
    private static final DotName PROTOBUF_INCOMING = DotName.createSimple("ai.pipestream.api.annotation.ProtobufIncoming");

    /** DotName for the {@link ai.pipestream.api.annotation.ProtobufChannel} annotation used on producer fields. */
    private static final DotName PROTOBUF_CHANNEL = DotName.createSimple("ai.pipestream.api.annotation.ProtobufChannel");

    /** Fully qualified class name of the Apicurio Protobuf Kafka Serializer. */
    private static final String PROTO_SER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer";

    /** Fully qualified class name of the Apicurio Protobuf Kafka Deserializer. */
    private static final String PROTO_DESER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer";

    /** Fully qualified class name of the Kafka UUID Serializer for message keys. */
    private static final String UUID_SER = "org.apache.kafka.common.serialization.UUIDSerializer";

    /** Fully qualified class name of the Kafka UUID Deserializer for message keys. */
    private static final String UUID_DESER = "org.apache.kafka.common.serialization.UUIDDeserializer";

    /**
     * Registers the pipeline-kafka-magic feature with Quarkus.
     *
     * <p>This feature name appears in the Quarkus startup banner and build logs,
     * indicating that the Pipeline Protobuf Kafka Connector extension is active.</p>
     *
     * @return A {@link FeatureBuildItem} that registers this extension's feature
     */
    @BuildStep
    FeatureBuildItem feature() {
        LOG.info("🔌 [Extension] Pipeline Protobuf Kafka Connector extension is loading...");
        LOG.info("🔌 [Extension] Registering feature: pipeline-kafka-magic");
        return new FeatureBuildItem("pipeline-kafka-magic");
    }

    /**
     * The core build step that performs automatic Kafka configuration generation.
     *
     * <p>This method implements the "No-Cheating" architecture by scanning the Jandex index
     * at build time to discover Protobuf message consumers and producers, then generating
     * the appropriate SmallRye Reactive Messaging configuration properties.</p>
     *
     * <h3>Processing Flow</h3>
     * <ol>
     *   <li>Registers {@link ApicurioConfigurator} as an unremovable bean</li>
     *   <li>Configures global Apicurio Registry URL bridging</li>
     *   <li>Scans for {@link ai.pipestream.api.annotation.ProtobufIncoming} annotations on methods</li>
     *   <li>Scans for {@link ai.pipestream.api.annotation.ProtobufChannel} annotations on fields</li>
     *   <li>Generates configuration for each discovered channel</li>
     * </ol>
     *
     * <h3>Configuration Generated</h3>
     * <p>For each consumer/producer channel, this method generates:</p>
     * <ul>
     *   <li>Topic name (derived from channel or explicit)</li>
     *   <li>Connector type (smallrye-kafka)</li>
     *   <li>Serializer/Deserializer classes</li>
     *   <li>Return class for deserialization (consumers only)</li>
     *   <li>Additional properties from annotation</li>
     * </ul>
     *
     * @param combinedIndex The combined Jandex index containing all application classes
     * @param props Producer for {@link SystemPropertyBuildItem}s that configure messaging channels
     * @param beans Producer for {@link AdditionalBeanBuildItem}s that register runtime beans
     *
     * @see #configureIncoming(BuildProducer, String, String, AnnotationInstance)
     * @see #configureOutgoing(BuildProducer, String, String, AnnotationInstance)
     * @see ApicurioConfigurator
     */
    @BuildStep
    void generateConfig(CombinedIndexBuildItem combinedIndex,
                        BuildProducer<SystemPropertyBuildItem> props,
                        BuildProducer<AdditionalBeanBuildItem> beans) {

        LOG.info("⚡ [Extension] Starting configuration generation...");

        // 1. Force the Runtime Enforcer (ApicurioConfigurator) to load
        beans.produce(AdditionalBeanBuildItem.unremovableOf(ApicurioConfigurator.class));

        // 2. Bridge Registry URL globally
        props.produce(new SystemPropertyBuildItem(
                "mp.messaging.connector.smallrye-kafka.apicurio.registry.url",
                "${apicurio.registry.url}"
        ));

        IndexView index = combinedIndex.getIndex();

        // 3. Configure CONSUMERS (@ProtobufIncoming on methods)
        for (AnnotationInstance annotation : index.getAnnotations(PROTOBUF_INCOMING)) {
            if (annotation.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
                MethodInfo method = annotation.target().asMethod();
                String channel = annotation.value().asString();

                if (!method.parameterTypes().isEmpty()) {
                    String typeName = method.parameterTypes().get(0).name().toString();
                    configureIncoming(props, channel, typeName, annotation);
                }
            }
        }

        // 4. Configure PRODUCERS (@ProtobufChannel on fields)
        for (AnnotationInstance annotation : index.getAnnotations(PROTOBUF_CHANNEL)) {
            if (annotation.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.FIELD) {
                FieldInfo field = annotation.target().asField();
                String channel = annotation.value().asString();

                // Extract the generic type from Emitter<T> or MutinyEmitter<T>
                String typeName = extractEmitterType(field.type());
                if (typeName != null) {
                    configureOutgoing(props, channel, typeName, annotation);
                }
            }
        }
    }

    /**
     * Configures a Kafka consumer channel for Protobuf message processing.
     *
     * <p>This method generates all necessary configuration properties for a consumer channel
     * annotated with {@link ai.pipestream.api.annotation.ProtobufIncoming}. It sets up
     * the topic, connector, deserializers, and most importantly, the return class that
     * tells Apicurio how to deserialize the Protobuf message.</p>
     *
     * <h3>Configuration Properties Generated</h3>
     * <ul>
     *   <li>{@code mp.messaging.incoming.{channel}.topic} - The Kafka topic name</li>
     *   <li>{@code mp.messaging.incoming.{channel}.connector} - Set to "smallrye-kafka"</li>
     *   <li>{@code mp.messaging.incoming.{channel}.key.deserializer} - UUID deserializer</li>
     *   <li>{@code mp.messaging.incoming.{channel}.value.deserializer} - Protobuf deserializer</li>
     *   <li>{@code mp.messaging.incoming.{channel}.apicurio.registry.deserializer.value.return-class} - The Protobuf class</li>
     *   <li>Additional properties from the annotation's {@code properties} array</li>
     * </ul>
     *
     * @param props The build producer for system property items
     * @param channel The channel name from the annotation (e.g., "orders-in")
     * @param typeName The fully qualified name of the Protobuf message class (e.g., "com.example.OrderEvent")
     * @param annotation The annotation instance containing additional configuration
     *
     * @see #deriveTopicName(String)
     * @see #processAnnotationProperties(BuildProducer, String, AnnotationInstance)
     */
    private void configureIncoming(BuildProducer<SystemPropertyBuildItem> props,
                                   String channel, String typeName, AnnotationInstance annotation) {
        String prefix = "mp.messaging.incoming." + channel;

        // A. Derive topic from channel (strip -in/-out) if not explicit
        AnnotationValue topicVal = annotation.value("topic");
        String topic = (topicVal != null && !topicVal.asString().isEmpty()) ? topicVal.asString() : deriveTopicName(channel);
        props.produce(new SystemPropertyBuildItem(prefix + ".topic", topic));

        // B. Inject the Return Class (critical for Protobuf deserialization)
        String returnClassKey = prefix + ".apicurio.registry.deserializer.value.return-class";
        props.produce(new SystemPropertyBuildItem(returnClassKey, typeName));

        // C. Force the connector and deserializers
        props.produce(new SystemPropertyBuildItem(prefix + ".connector", "smallrye-kafka"));
        props.produce(new SystemPropertyBuildItem(prefix + ".key.deserializer", UUID_DESER));
        props.produce(new SystemPropertyBuildItem(prefix + ".value.deserializer", PROTO_DESER));

        // D. Process properties array from annotation
        processAnnotationProperties(props, prefix, annotation);

        LOG.infof("⚡ [Extension] Configured CONSUMER %s -> %s (topic: %s)", channel, typeName, topic);
    }

    /**
     * Configures a Kafka producer channel for Protobuf message processing.
     *
     * <p>This method generates all necessary configuration properties for a producer channel
     * annotated with {@link ai.pipestream.api.annotation.ProtobufChannel}. It sets up
     * the topic, connector, and serializers for sending Protobuf messages to Kafka.</p>
     *
     * <h3>Configuration Properties Generated</h3>
     * <ul>
     *   <li>{@code mp.messaging.outgoing.{channel}.topic} - The Kafka topic name</li>
     *   <li>{@code mp.messaging.outgoing.{channel}.connector} - Set to "smallrye-kafka"</li>
     *   <li>{@code mp.messaging.outgoing.{channel}.key.serializer} - UUID serializer</li>
     *   <li>{@code mp.messaging.outgoing.{channel}.value.serializer} - Protobuf serializer</li>
     *   <li>Additional properties from the annotation's {@code properties} array</li>
     * </ul>
     *
     * @param props The build producer for system property items
     * @param channel The channel name from the annotation (e.g., "orders-out")
     * @param typeName The fully qualified name of the Protobuf message class being sent
     * @param annotation The annotation instance containing additional configuration
     *
     * @see #deriveTopicName(String)
     * @see #processAnnotationProperties(BuildProducer, String, AnnotationInstance)
     */
    private void configureOutgoing(BuildProducer<SystemPropertyBuildItem> props,
                                   String channel, String typeName, AnnotationInstance annotation) {
        String prefix = "mp.messaging.outgoing." + channel;

        // A. Derive topic from channel (strip -in/-out) if not explicit
        AnnotationValue topicVal = annotation.value("topic");
        String topic = (topicVal != null && !topicVal.asString().isEmpty()) ? topicVal.asString() : deriveTopicName(channel);
        props.produce(new SystemPropertyBuildItem(prefix + ".topic", topic));

        // B. Force the connector and serializers
        props.produce(new SystemPropertyBuildItem(prefix + ".connector", "smallrye-kafka"));
        props.produce(new SystemPropertyBuildItem(prefix + ".key.serializer", UUID_SER));
        props.produce(new SystemPropertyBuildItem(prefix + ".value.serializer", PROTO_SER));

        // C. Process properties array from annotation
        processAnnotationProperties(props, prefix, annotation);

        LOG.infof("⚡ [Extension] Configured PRODUCER %s -> %s (topic: %s)", channel, typeName, topic);
    }

    /**
     * Derives the Kafka topic name from a channel name.
     *
     * <p>This method implements a simple convention for topic naming:</p>
     * <ul>
     *   <li>Channels ending in "-in" have the suffix removed (e.g., "orders-in" → "orders")</li>
     *   <li>Channels ending in "-out" have the suffix removed (e.g., "orders-out" → "orders")</li>
     *   <li>Other channels are used as-is</li>
     * </ul>
     *
     * <p>This allows using descriptive channel names like "orders-in" and "orders-out"
     * while mapping them to the same underlying Kafka topic "orders".</p>
     *
     * @param channel The channel name from the annotation
     * @return The derived topic name, or the original channel name if no derivation applies
     *
     * @see ai.pipestream.api.annotation.ProtobufIncoming#value()
     * @see ai.pipestream.api.annotation.ProtobufChannel#value()
     */
    private String deriveTopicName(String channel) {
        if (channel.endsWith("-in")) {
            return channel.substring(0, channel.length() - 3);
        } else if (channel.endsWith("-out")) {
            return channel.substring(0, channel.length() - 4);
        }
        return channel;
    }

    /**
     * Processes additional properties specified in annotation arrays.
     *
     * <p>This method parses the {@code properties} array from
     * {@link ai.pipestream.api.annotation.ProtobufIncoming} or
     * {@link ai.pipestream.api.annotation.ProtobufChannel} annotations and converts
     * them into system properties.</p>
     *
     * <h3>Property Format</h3>
     * <p>Each property string should be in the format "key=value". For example:</p>
     * <pre>{@code
     * @ProtobufIncoming(value = "orders", properties = {
     *     "fetch.min.bytes=500",
     *     "apicurio.registry.find-latest=false"
     * })
     * }</pre>
     *
     * @param props The build producer for system property items
     * @param prefix The configuration prefix (e.g., "mp.messaging.incoming.orders")
     * @param annotation The annotation instance containing the properties array
     *
     * @see ai.pipestream.api.annotation.ProtobufIncoming#properties()
     * @see ai.pipestream.api.annotation.ProtobufChannel#properties()
     */
    private void processAnnotationProperties(BuildProducer<SystemPropertyBuildItem> props,
                                             String prefix, AnnotationInstance annotation) {
        AnnotationValue propertiesValue = annotation.value("properties");
        if (propertiesValue != null) {
            for (String prop : propertiesValue.asStringArray()) {
                int idx = prop.indexOf('=');
                if (idx > 0) {
                    String key = prop.substring(0, idx).trim();
                    String value = prop.substring(idx + 1).trim();
                    String fullKey = prefix + "." + key;
                    props.produce(new SystemPropertyBuildItem(fullKey, value));
                    LOG.infof("🔧 [Config] %s = %s (from annotation)", fullKey, value);
                }
            }
        }
    }

    /**
     * Extracts the generic type parameter from Emitter or MutinyEmitter fields.
     *
     * <p>This method analyzes the type of a field annotated with
     * {@link ai.pipestream.api.annotation.ProtobufChannel} to determine the
     * Protobuf message type being emitted.</p>
     *
     * <h3>Supported Types</h3>
     * <ul>
     *   <li>{@code Emitter<OrderEvent>} → "com.example.OrderEvent"</li>
     *   <li>{@code MutinyEmitter<UserCreated>} → "com.example.UserCreated"</li>
     *   <li>Non-parameterized types → null</li>
     * </ul>
     *
     * @param fieldType The Jandex Type of the annotated field
     * @return The fully qualified class name of the emitted type, or null if extraction fails
     *
     * @see org.eclipse.microprofile.reactive.messaging.Emitter
     * @see io.smallrye.mutiny.helpers.test.UniAssertSubscriber#MutinyEmitter
     */
    private String extractEmitterType(Type fieldType) {
        // Handle Emitter<T> or MutinyEmitter<T>
        if (fieldType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            List<Type> args = fieldType.asParameterizedType().arguments();
            if (!args.isEmpty()) {
                return args.get(0).name().toString();
            }
        }
        return null;
    }
}