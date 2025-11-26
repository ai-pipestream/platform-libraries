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

public class ProtobufKafkaProcessor {

    private static final Logger LOG = Logger.getLogger(ProtobufKafkaProcessor.class);

    private static final DotName PROTOBUF_INCOMING = DotName.createSimple("ai.pipestream.api.annotation.ProtobufIncoming");
    private static final DotName PROTOBUF_CHANNEL = DotName.createSimple("ai.pipestream.api.annotation.ProtobufChannel");

    private static final String PROTO_SER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer";
    private static final String PROTO_DESER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer";
    private static final String UUID_SER = "org.apache.kafka.common.serialization.UUIDSerializer";
    private static final String UUID_DESER = "org.apache.kafka.common.serialization.UUIDDeserializer";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("pipeline-kafka-magic");
    }

    /**
     * THE BRAIN: Scans code at build time and enforces the configuration.
     * Handles both consumers (@ProtobufIncoming) and producers (@ProtobufChannel).
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

    private String deriveTopicName(String channel) {
        if (channel.endsWith("-in")) {
            return channel.substring(0, channel.length() - 3);
        } else if (channel.endsWith("-out")) {
            return channel.substring(0, channel.length() - 4);
        }
        return channel;
    }

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