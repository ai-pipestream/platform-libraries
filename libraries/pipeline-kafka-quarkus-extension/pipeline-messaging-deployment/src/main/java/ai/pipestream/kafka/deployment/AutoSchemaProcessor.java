package ai.pipestream.kafka.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import org.jboss.jandex.*;

import java.util.List;

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
    private static final DotName INCOMING = DotName
            .createSimple("org.eclipse.microprofile.reactive.messaging.Incoming");

    @BuildStep
    public void inferProtobufTypes(CombinedIndexBuildItem index,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> configProducer) {

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

                        // AUTOMATICALLY GENERATE CONFIG
                        // This sets the 'return-class' property for the Apicurio deserializer
                        // so the user doesn't have to manually configure it.
                        String configKey = "mp.messaging.incoming." + channelName
                                + ".apicurio.registry.deserializer.value.return-class";

                        configProducer.produce(new RunTimeConfigurationDefaultBuildItem(configKey, className));
                    }
                }
            }
        }
    }

    private boolean isProtobuf(ClassInfo classInfo, IndexView index) {
        // Check if it implements com.google.protobuf.Message
        DotName messageInterface = DotName.createSimple("com.google.protobuf.Message");

        // Check direct interfaces
        if (classInfo.interfaceNames().contains(messageInterface)) {
            return true;
        }

        // Check superclass (Protobuf classes usually extend GeneratedMessageV3)
        Type superType = classInfo.superClassType();
        if (superType != null && superType.kind() == Type.Kind.CLASS) {
            ClassInfo superClass = index.getClassByName(superType.name());
            if (superClass != null && !superClass.name().toString().equals("java.lang.Object")) {
                return isProtobuf(superClass, index);
            }
        }

        return false;
    }
}
