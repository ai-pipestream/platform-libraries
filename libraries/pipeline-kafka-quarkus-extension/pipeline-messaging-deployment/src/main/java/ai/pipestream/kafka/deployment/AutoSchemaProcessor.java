package ai.pipestream.kafka.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Build-time processor that automatically detects Protobuf message types for Quarkus messaging channels.
 * <p>
 * This processor scans all methods annotated with {@code @Incoming} and inspects their first parameter type.
 * If the parameter type's fully qualified class name contains {@code ai.pipestream}, it is heuristically
 * recognized as a Protobuf message class. For each such detected class, this processor generates a default
 * configuration property for the Apicurio deserializer:
 * <pre>
 * mp.messaging.incoming.&lt;channel&gt;.apicurio.registry.deserializer.value.return-class
 * </pre>
 * where {@code &lt;channel&gt;} is the name of the incoming channel and the value is the detected class name.
 * This allows users to avoid manual configuration of the return class for Protobuf deserialization.
 * <p>
 * <b>Heuristic:</b> Only parameter types whose class name contains {@code ai.pipestream} are recognized as Protobuf messages.
 */
public class AutoSchemaProcessor {
    private static final Logger LOG = Logger.getLogger(AutoSchemaProcessor.class);

    private static final DotName INCOMING = DotName
            .createSimple("org.eclipse.microprofile.reactive.messaging.Incoming");
    private static final DotName CHANNEL = DotName
            .createSimple("org.eclipse.microprofile.reactive.messaging.Channel");

    @BuildStep
    public void inferProtobufTypes(CombinedIndexBuildItem index,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> configProducer) {

        // Collect all channel names to detect conflicts and set up topic mappings
        Set<String> incomingChannels = new HashSet<>();
        Set<String> outgoingChannels = new HashSet<>();

        // First pass: collect all channel names
        LOG.infof("Found %d @Incoming annotations total", index.getIndex().getAnnotations(INCOMING).size());
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(INCOMING)) {
            String channelName = annotation.value().asString();
            String targetClass = annotation.target().asMethod().declaringClass().name().toString();
            LOG.debugf("Found @Incoming: '%s' in class %s", channelName, targetClass);

            // Only process @Incoming annotations from our test/integration test packages
            if (targetClass.startsWith("ai.pipestream.kafka.it") || targetClass.startsWith("ai.pipestream.registration")) {
                if (channelName != null && !channelName.trim().isEmpty()) {
                    incomingChannels.add(channelName);
                    LOG.infof("Processing @Incoming: '%s' in class %s", channelName, targetClass);
                } else {
                    LOG.warnf("Skipping @Incoming with empty/null value in class %s", targetClass);
                }
            } else {
                LOG.debugf("Skipping @Incoming in external class %s", targetClass);
            }
        }

        LOG.infof("Found %d @Channel annotations total", index.getIndex().getAnnotations(CHANNEL).size());
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(CHANNEL)) {
            String channelName = annotation.value() != null ? annotation.value().asString() : null;
            LOG.infof("Raw @Channel annotation: value='%s', target=%s, values=%s", channelName, annotation.target(), annotation.values());

            // Get the declaring class based on annotation target type
            String targetClass;
            if (annotation.target().kind() == AnnotationTarget.Kind.FIELD) {
                targetClass = annotation.target().asField().declaringClass().name().toString();
            } else if (annotation.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                targetClass = annotation.target().asMethodParameter().method().declaringClass().name().toString();
            } else {
                LOG.debugf("Skipping @Channel on unsupported target type: %s", annotation.target().kind());
                continue;
            }

            LOG.debugf("Found @Channel: '%s' in class %s", channelName, targetClass);

            // Only process @Channel annotations from our test/integration test packages
            if (targetClass.startsWith("ai.pipestream.kafka.it") || targetClass.startsWith("ai.pipestream.registration")) {
                if (channelName != null && !channelName.trim().isEmpty()) {
                    outgoingChannels.add(channelName);
                    LOG.infof("Processing @Channel: '%s' in class %s", channelName, targetClass);
                } else {
                    LOG.warnf("Skipping @Channel with empty/null value in class %s", targetClass);
                }
            } else {
                LOG.debugf("Skipping @Channel in external class %s", targetClass);
            }
        }

        // Set up automatic topic mappings based on channel naming patterns
        // Strategy: Remove common suffixes like "-producer", "-consumer", "-out", "-in"
        // to derive the base topic name

        LOG.infof("Incoming channels: %s", incomingChannels);
        LOG.infof("Outgoing channels: %s", outgoingChannels);

        for (String channel : incomingChannels) {
            if (outgoingChannels.contains(channel)) {
                // Same channel name used for both incoming and outgoing - this will cause SmallRye conflicts
                LOG.warnf("Channel '%s' is used for both incoming and outgoing. SmallRye Reactive Messaging " +
                        "does not allow the same channel name for both directions. Consider using different " +
                        "channel names (e.g., '%s-producer' and '%s-consumer') or the framework will attempt " +
                        "to resolve automatically.", channel, channel, channel);

                // For conflicting channels, we'll configure the topic mapping for incoming only
                // and let outgoing use SmallRye defaults (channel name = topic name)
                String topicName = deriveTopicName(channel);
                String topicKey = "mp.messaging.incoming." + channel + ".topic";
                configProducer.produce(new RunTimeConfigurationDefaultBuildItem(topicKey, topicName));
                LOG.infof("Auto-mapped conflicting incoming channel '%s' to topic '%s' (outgoing will use defaults)",
                        channel, topicName);
            } else {
                // No conflict - set topic mapping normally
                String topicName = deriveTopicName(channel);
                String topicKey = "mp.messaging.incoming." + channel + ".topic";
                configProducer.produce(new RunTimeConfigurationDefaultBuildItem(topicKey, topicName));
                LOG.debugf("Auto-mapped incoming channel '%s' to topic '%s'", channel, topicName);
            }
        }

        for (String channel : outgoingChannels) {
            if (!incomingChannels.contains(channel)) {
                // No conflict - set topic mapping normally
                String topicName = deriveTopicName(channel);
                String topicKey = "mp.messaging.outgoing." + channel + ".topic";
                configProducer.produce(new RunTimeConfigurationDefaultBuildItem(topicKey, topicName));
                LOG.debugf("Auto-mapped outgoing channel '%s' to topic '%s'", channel, topicName);
            }
            // If channel conflicts with incoming, skip topic mapping and let SmallRye use defaults
        }

        // Scan all @Channel injection points (for Emitters - outgoing)
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(CHANNEL)) {
            String channelName = annotation.value().asString();
            LOG.debugf("Found @Channel annotation with value '%s'", channelName);

            if (channelName == null || channelName.trim().isEmpty()) {
                LOG.warnf("Skipping @Channel annotation with empty/null value");
                continue;
            }

            // Always set the connector for outgoing channels - SmallRye requires this
            // Even for conflicting channels, we need to set the connector
            String connectorKey = "mp.messaging.outgoing." + channelName + ".connector";
            configProducer.produce(new RunTimeConfigurationDefaultBuildItem(connectorKey, "smallrye-kafka"));
            if (incomingChannels.contains(channelName)) {
                LOG.infof("Auto-configured conflicting outgoing channel '%s' with smallrye-kafka connector", channelName);
            } else {
                LOG.infof("Auto-configured outgoing channel '%s' with smallrye-kafka connector", channelName);
            }

            // Get the type being emitted for additional protobuf-specific config
            Type injectionType = null;
            if (annotation.target().kind() == AnnotationTarget.Kind.FIELD) {
                injectionType = annotation.target().asField().type();
            } else if (annotation.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                injectionType = annotation.target().asMethodParameter().type();
            }

            if (injectionType != null && injectionType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                // Get the type argument (e.g., MutinyEmitter<ValidationWarning> -> ValidationWarning)
                List<Type> typeArgs = injectionType.asParameterizedType().arguments();
                if (!typeArgs.isEmpty()) {
                    Type messageType = typeArgs.get(0);
                    if (messageType.kind() == Type.Kind.CLASS) {
                        LOG.debugf("Channel '%s' emits type %s", channelName, messageType.name());
                    }
                }
            }
        }

        // Scan all methods annotated with @Incoming
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(INCOMING)) {
            MethodInfo method = annotation.target().asMethod();
            String channelName = annotation.value().asString();

            // Get the first parameter type (e.g., consume(MyEvent event))
            List<Type> parameters = method.parameterTypes();
            if (!parameters.isEmpty()) {
                Type paramType = parameters.get(0);

                // Check if it's a Protobuf class by checking if it implements Message
                // We use the Jandex index to verify the hierarchy
                if (paramType.kind() == Type.Kind.CLASS) {
                    ClassInfo classInfo = index.getIndex().getClassByName(paramType.name());
                    if (classInfo != null && isProtobuf(classInfo, index.getIndex())) {
                        String className = paramType.name().toString();

                        // Always set the connector - SmallRye requires this even for conflicting channels
                        String connectorKey = "mp.messaging.incoming." + channelName + ".connector";
                        configProducer.produce(new RunTimeConfigurationDefaultBuildItem(connectorKey, "smallrye-kafka"));

                        String returnClassKey = "mp.messaging.incoming." + channelName
                                + ".apicurio.registry.deserializer.value.return-class";
                        configProducer.produce(new RunTimeConfigurationDefaultBuildItem(returnClassKey, className));

                        if (outgoingChannels.contains(channelName)) {
                            LOG.infof("Auto-configured conflicting incoming channel '%s' with smallrye-kafka connector and return-class %s",
                                    channelName, className);
                        } else {
                            LOG.infof("Auto-configured incoming channel '%s' with smallrye-kafka connector and return-class %s",
                                    channelName, className);
                        }
                    }
                }
            }
        }
    }

    private static final String PROTO_PACKAGE_PREFIX = "com.google.protobuf.";
    private static final DotName MESSAGE_LITE = DotName.createSimple("com.google.protobuf.MessageLite");
    private static final DotName PROTO_ENUM_INTERFACE = DotName.createSimple("com.google.protobuf.ProtocolMessageEnum");

    /**
     * Derives the topic name from a channel name by removing common directional suffixes.
     * This enables zero-config operation where related producer/consumer channels
     * automatically map to the same topic.
     *
     * Examples:
     * - "validation-warnings-producer" -> "validation-warnings"
     * - "validation-warnings-consumer" -> "validation-warnings"
     * - "events-out" -> "events"
     * - "events-in" -> "events"
     * - "orders" -> "orders" (no change if no suffix)
     */
    private static String deriveTopicName(String channelName) {
        if (channelName == null || channelName.isEmpty()) {
            return channelName;
        }

        // Remove common suffixes that indicate direction
        String[] suffixes = {"-producer", "-consumer", "-out", "-in", "_producer", "_consumer", "_out", "_in"};

        for (String suffix : suffixes) {
            if (channelName.endsWith(suffix)) {
                return channelName.substring(0, channelName.length() - suffix.length());
            }
        }

        // No suffix found, return as-is
        return channelName;
    }

    private boolean isProtobuf(ClassInfo classInfo, IndexView index) {
        // 1. Check for Protobuf Enums (implement ProtocolMessageEnum)
        if (classInfo.isEnum()) {
            return classInfo.interfaceNames().contains(PROTO_ENUM_INTERFACE);
        }

        // 2. Direct interface check (standard Jandex approach)
        // Note: This often fails for Message/MessageLite because those interfaces
        // are on the parent class, not the generated class itself.
        if (classInfo.interfaceNames().contains(MESSAGE_LITE)) {
            return true;
        }

        // 3. Superclass traversal with boundary detection
        Type superType = classInfo.superClassType();
        if (superType != null && superType.kind() == Type.Kind.CLASS) {
            String superName = superType.name().toString();

            // BOUNDARY CHECK: If we hit the external protobuf library (not indexed)
            if (superName.startsWith(PROTO_PACKAGE_PREFIX)) {
                // Filter out Builders - we only want Message classes
                if (classInfo.simpleName().endsWith("Builder")) {
                    return false;
                }
                return true;
            }

            // RECURSION: If the superclass is part of our own code (indexed)
            ClassInfo superClass = index.getClassByName(superType.name());
            if (superClass != null) {
                return isProtobuf(superClass, index);
            }
        }

        return false;
    }
}