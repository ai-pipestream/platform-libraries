# Kafka Configuration Guide

## Overview
The Pipeline Platform provides **zero-config Kafka** through the `pipeline-kafka-quarkus-extension`. This extension automatically handles all Kafka configuration, topic mapping, and Protobuf serialization.

## What the Extension Does Automatically

The extension (`ai.pipestream:pipeline-kafka-quarkus-extension`) provides:

*   **Automatic Topic Mapping:** Channel names automatically map to Kafka topics
*   **Connector Auto-Setup:** Automatically configures `smallrye-kafka` connectors
*   **Protobuf Serialization/Deserialization:** Automatically configures serializers, deserializers, and return-classes
*   **Platform Standards:** Enforces UUID keys, Protobuf values, reliability settings
*   **Schema Registration:** Auto-registers Protobuf schemas with Apicurio

**What You Still Configure:**
*   `kafka.bootstrap.servers` (infrastructure URL)
*   `mp.messaging.connector.smallrye-kafka.apicurio.registry.url` (infrastructure URL)
*   Optional custom topic mappings (if needed)

**The extension handles all the complex Kafka configuration - you only provide the infrastructure URLs.**

## Legacy Configuration (Still Works)

The original `PipelineKafkaConfigSource` in `pipeline-commons` still provides global defaults, but the extension makes manual configuration unnecessary.

## Usage (New Extension Approach - RECOMMENDED)

### 1. Dependencies
Add the Kafka extension to your `build.gradle`:

```groovy
dependencies {
    implementation platform('ai.pipestream:pipeline-bom:0.2.10') // or later
    implementation 'ai.pipestream:pipeline-kafka-quarkus-extension'  // This handles everything!
    implementation 'ai.pipestream:grpc-stubs'  // Your protobuf types
}
```

### 2. Infrastructure Configuration (Still Required)

You MUST configure the infrastructure URLs in all environments:

```properties
# REQUIRED: Infrastructure settings
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

### 3. Application Code (Zero Kafka Config Needed!)

Just use channel names in your code. The extension handles all Kafka-related configuration automatically.

**Producer:**
```java
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class ValidationPublisher {
    @Channel("validation-events-producer")  // Automatically maps to topic "validation-events"
    MutinyEmitter<ValidationEvent> emitter;

    public Uni<Void> publishValidation(ValidationEvent event) {
        return emitter.send(event);  // UUID key + Protobuf serialization = automatic
    }
}
```

**Consumer:**
```java
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import java.util.UUID;

@ApplicationScoped
public class ValidationConsumer {
    @Incoming("validation-events-consumer")  // Automatically maps to topic "validation-events"
    public Uni<Void> consume(ConsumerRecord<UUID, ValidationEvent> record) {
        // Automatic UUID key deserialization + Protobuf value deserialization
        return processValidation(record.value());
    }
}
```

### 3. Automatic Topic Mapping

The extension automatically maps channel names to topics using directional suffixes. **Never use the same channel name for both @Channel and @Incoming simultaneously - this causes SmallRye conflicts. Single-direction usage works fine:**

- ✅ `@Channel("validation-events-producer")` → topic `"validation-events"`
- ✅ `@Incoming("validation-events-consumer")` → topic `"validation-events"`
- ❌ `@Channel("events")` + `@Incoming("events")` → **CONFLICTS!**

### 4. Optional: Custom Topic Names

If you need a channel to use a different topic name:

```properties
mp.messaging.outgoing.my-channel.topic=custom-topic-name
mp.messaging.incoming.my-channel.topic=custom-topic-name
```

**That's it!** No serializers, deserializers, registry URLs, or connector settings needed.

## Testing with the Extension

The extension makes testing much simpler - all the complex Kafka configuration is handled automatically.

### 1. Test Infrastructure
**`src/test/resources/compose-test-services.yml`** (standard infrastructure)
```yaml
version: '3.8'
services:
  kafka-test:
    image: redpandadata/redpanda:latest
    # ... standard kafka config ...
  apicurio-registry-test:
    image: apicurio/apicurio-registry:3.0.12
    # ... standard apicurio config ...
```

### 2. Test Configuration
**`src/test/resources/application.properties`**
```properties
# Enable test infrastructure
%test.quarkus.compose.devservices.enabled=true
%test.quarkus.compose.devservices.files=src/test/resources/compose-test-services.yml

# Infrastructure URLs (provided by compose-devservices)
%test.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9095}
%test.mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL:http://localhost:8082/apis/registry/v3}

# The extension handles ALL OTHER Kafka configuration automatically!
# No manual serializers, deserializers, connector settings, etc. needed!
```

### 3. Writing Tests

**Producer Test:**
```java
@QuarkusTest
public class MyPublisherTest {
    @Inject
    MyPublisher publisher;  // Your service with @Channel injection

    @Test
    public void testEventIsPublished() {
        // The extension handles all Kafka setup automatically
        publisher.publishEvent(new MyEvent());
        // Verify via downstream effects or integration assertions
    }
}
```

**Consumer Test:**
```java
@QuarkusTest
public class MyConsumerTest {
    @InjectMock
    DownstreamService mockService;

    @Test
    public void testConsumer() {
        // The extension automatically configures the consumer
        // Send test messages and verify mockService was called
    }
}
```

## Migration from Manual Configuration

### What Changes

**Before (Manual Configuration):**
```groovy
dependencies {
    implementation 'ai.pipestream:pipeline-commons'  // Only this
}
```

```properties
# Lots of manual configuration needed
kafka.bootstrap.servers=...
mp.messaging.connector.smallrye-kafka.apicurio.registry.url=...
mp.messaging.outgoing.events.connector=smallrye-kafka
mp.messaging.outgoing.events.topic=events
mp.messaging.incoming.events.connector=smallrye-kafka
mp.messaging.incoming.events.topic=events
# Plus global configs for serializers, registry settings, etc.
```

**After (Extension):**
```groovy
dependencies {
    implementation 'ai.pipestream:pipeline-kafka-quarkus-extension'  // Just this
}
```

```properties
# Only infrastructure URLs needed - extension handles everything else!
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

### Migration Steps

1. **Add the extension dependency** to your `build.gradle`
2. **Remove all manual Kafka configuration** from `application.properties`
3. **Update channel names** if needed (use directional suffixes for producer/consumer pairs)
4. **Remove test configuration** for serializers/deserializers (extension handles it)

### What the Extension Does

- ✅ **Automatic topic mapping** from channel names
- ✅ **Automatic connector setup** for all detected channels
- ✅ **Automatic Protobuf deserialization** with correct return types
- ✅ **Platform standard enforcement** (UUID keys, reliability settings)
- ✅ **Simplified testing** with automatic configuration

**Developers are NOT allowed to configure Kafka manually anymore. The extension handles everything.**
