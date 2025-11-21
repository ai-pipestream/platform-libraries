package ai.pipestream.grpc.util;

import ai.pipestream.platform.registration.ModuleRegistered;
import ai.pipestream.platform.registration.ModuleUnregistered;
import ai.pipestream.platform.registration.ServiceRegistered;
import ai.pipestream.platform.registration.ServiceUnregistered;
import ai.pipestream.repository.account.AccountEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Type-safe utility for generating deterministic Kafka keys from Protobuf events.
 * <p>
 * This ensures that every event type uses the correct identity field for its Kafka partition key,
 * guaranteeing correct log compaction.
 */
public class KafkaProtobufKeys {

    private KafkaProtobufKeys() {
        // Utility class
    }

    /**
     * Generates a deterministic UUID key for AccountEvent based on Account ID.
     */
    public static UUID uuid(AccountEvent event) {
        return uuidFrom(event.getAccountId());
    }

    /**
     * Generates a deterministic UUID key for ServiceRegistered based on Service ID.
     */
    public static UUID uuid(ServiceRegistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for ServiceUnregistered based on Service ID.
     */
    public static UUID uuid(ServiceUnregistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for ModuleRegistered based on Service ID.
     */
    public static UUID uuid(ModuleRegistered event) {
        return uuidFrom(event.getServiceId());
    }

    /**
     * Generates a deterministic UUID key for ModuleUnregistered based on Service ID.
     */
    public static UUID uuid(ModuleUnregistered event) {
        return uuidFrom(event.getServiceId());
    }

    private static UUID uuidFrom(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null for Kafka key generation");
        }
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
    }
}
