package ai.pipestream.kafka.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import org.jboss.jandex.*;

import java.util.List;

public class AutoSchemaProcessor {

    private static final DotName INCOMING = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Incoming");

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
                
                // If it's a Protobuf class (heuristic: package name or interface)
                // We check if it starts with ai.pipestream or ends with Proto
                if (paramType.name().toString().contains("ai.pipestream")) {
                    String className = paramType.name().toString();
                    
                    // AUTOMATICALLY GENERATE CONFIG
                    // This sets the 'return-class' property for the Apicurio deserializer
                    // so the user doesn't have to manually configure it.
                    String configKey = "mp.messaging.incoming." + channelName + ".apicurio.registry.deserializer.value.return-class";
                    
                    configProducer.produce(new RunTimeConfigurationDefaultBuildItem(configKey, className));
                }
            }
        }
    }
}
