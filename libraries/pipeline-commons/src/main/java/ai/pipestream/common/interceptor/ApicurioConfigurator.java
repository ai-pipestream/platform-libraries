package ai.pipestream.common.interceptor;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.ClientCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Identifier("smallrye-kafka")
public class ApicurioConfigurator implements ClientCustomizer<Map<String, Object>> {

    private static final Logger LOG = Logger.getLogger(ApicurioConfigurator.class);

    @Inject
    Config config;
    
    // Fixed Sensitive Keys: Explicitly excluded "serializer" to allow debugging
    private static final Set<String> SENSITIVE_TERMS = Set.of("password", "secret", "token", "apikey");


    @Override
    public Map<String, Object> customize(String channelName, Config channelConfig, Map<String, Object> kafkaConfig) {
        LOG.debugf("🔧 [ApicurioConfigurator] Enforcing defaults for: %s", channelName);

        // 1. FORCE SERIALIZERS (The Sledgehammer)
        // This prevents SmallRye from ever guessing "Jackson"
        kafkaConfig.put("key.deserializer", "org.apache.kafka.common.serialization.UUIDDeserializer");
        kafkaConfig.put("value.deserializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer");
        kafkaConfig.put("key.serializer", "org.apache.kafka.common.serialization.UUIDSerializer");
        kafkaConfig.put("value.serializer", "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer");

        // 2. APICURIO SETTINGS
        // The URL is already bridged by the extension, but setting it here covers edge cases
        String registryUrl = config.getOptionalValue("apicurio.registry.url", String.class)
                .orElse("http://localhost:8080/apis/registry/v3");

        putAndLog(kafkaConfig, "apicurio.registry.url", registryUrl);
        putAndLog(kafkaConfig, "apicurio.registry.auto-register", "true");
        putAndLog(kafkaConfig, "apicurio.registry.find-latest", "true");

        // Note: 'return-class' is handled by the Build Step (System Properties).
        // We rely on that injection.

        return kafkaConfig;
    }

    private void putAndLog(Map<String, Object> config, String key, Object value) {
        config.put(key, value);
        if (isSensitive(key)) {
            LOG.debugf("  SET %s = [REDACTED]", key);
        } else {
            LOG.debugf("  SET %s = %s", key, value);
        }
    }

    private boolean isSensitive(String key) {
        String lower = key.toLowerCase();
        // Don't redact config keys like "value.serializer" even though they contain "s-e-r-..."
        if (lower.contains("serializer") || lower.contains("deserializer")) return false;
        return SENSITIVE_TERMS.stream().anyMatch(lower::contains);
    }
}