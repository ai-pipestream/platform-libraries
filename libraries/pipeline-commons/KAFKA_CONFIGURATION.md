# Kafka Setup Guide

## Setup (3 Steps)

**Add Kafka messaging to your Quarkus service:**

1. **Add dependency** to `build.gradle`:
   ```groovy
   implementation 'ai.pipestream:pipeline-kafka-quarkus-extension'
   ```

2. **Add infrastructure URLs** to `application.properties`:
   ```properties
   kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
   mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL}
   ```

3. **Use in your code:**
   ```java
   // Both emitter types work automatically
   @Channel("events-producer") MutinyEmitter<MyEvent> emitter;
   @Incoming("events-consumer") ConsumerRecord<UUID, MyEvent> consume(record);
   ```

**That's it!** The extension handles all Kafka configuration automatically.

## What the Extension Does

- ✅ **Automatic topic mapping** from channel names
- ✅ **Zero-config Protobuf** serialization/deserialization
- ✅ **Apicurio schema management** and evolution
- ✅ **Platform standards** (UUID keys, reliability settings)
- ✅ **Both emitter types** supported (MutinyEmitter & Emitter)

## Production Setup

**Required configuration** for all production environments:

```properties
# REQUIRED: Infrastructure URLs
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

**Optional:** Custom topic mapping if needed:
```properties
mp.messaging.outgoing.my-channel.topic=custom-topic-name
mp.messaging.incoming.my-channel.topic=custom-topic-name
```

**That's all!** The extension handles everything else automatically.

## Test Environment

**Test setup uses compose-devservices** - no manual configuration needed.

1. **Create test infrastructure** (`src/test/resources/compose-test-services.yml`):
   ```yaml
   version: '3.8'
   services:
     kafka-test:
       image: redpandadata/redpanda:latest
       # Kafka configuration...
     apicurio-registry-test:
       image: apicurio/apicurio-registry:3.1.2
       # Apicurio configuration...
   ```

2. **Enable automatic test services** (`src/test/resources/application.properties`):
   ```properties
   %test.quarkus.compose.devservices.enabled=true
   %test.quarkus.compose.devservices.files=src/test/resources/compose-test-services.yml
   ```

**The extension automatically connects to test services - no URLs needed!**

## Development Environment

**Dev uses real Kafka/Apicurio** with database persistence.

1. **Infrastructure URLs** are provided automatically via environment variables or config
2. **Same configuration as production** - just different URL values
3. **Database-backed** Apicurio registry persists schemas between restarts

```properties
# Dev URLs (provided via environment/deployment)
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
mp.messaging.connector.smallrye-kafka.apicurio.registry.url=${APICURIO_REGISTRY_URL}
```

## Code Examples

### Producer (Both Emitter Types Work)
```java
@Channel("user-events-producer")
MutinyEmitter<UserEvent> mutinyEmitter;

// OR

@Channel("user-events-producer")
Emitter<UserEvent> emitter;
```

### Consumer
```java
@Incoming("user-events-consumer")
public Uni<Void> consume(ConsumerRecord<UUID, UserEvent> record) {
    // Automatic UUID key + Protobuf value deserialization
    return processEvent(record.value());
}
```

## Troubleshooting Common Issues

### Messages not being sent/received
- **Infrastructure URLs set?** Check `kafka.bootstrap.servers` and `apicurio.registry.url`
- **Channel name conflicts?** Don't use same name for `@Channel` and `@Incoming`
- **Extension dependency?** Verify `pipeline-kafka-quarkus-extension` is in `build.gradle`

### Serialization errors
- **Protobuf classes?** Extension only works with Protobuf messages from `grpc-stubs`
- **Consumer signature?** Must use `ConsumerRecord<UUID, YourProtobufType>`

### Build errors
- **Channel names?** Use directional suffixes like `-producer`, `-consumer`
- **Dependencies?** Need `pipeline-commons`, `grpc-stubs`, and the extension

### Need custom topic names
```properties
mp.messaging.outgoing.my-channel.topic=custom-topic-name
mp.messaging.incoming.my-channel.topic=custom-topic-name
```

## Legacy Configuration (DEPRECATED)

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

## What NOT to Do (Important!)

❌ **DO NOT** add these to `application.properties`:
- `mp.messaging.outgoing.*.connector=smallrye-kafka`
- `mp.messaging.incoming.*.connector=smallrye-kafka`
- `mp.messaging.*.*.key.serializer=*`
- `mp.messaging.*.*.value.serializer=*`
- `mp.messaging.*.*.key.deserializer=*`
- `mp.messaging.*.*.value.deserializer=*`
- Any Apicurio registry settings except the URL

❌ **DO NOT** configure serializers/deserializers in code or tests

❌ **DO NOT** use the same channel name for both `@Channel` and `@Incoming`

✅ **ONLY** configure:
- `kafka.bootstrap.servers`
- `mp.messaging.connector.smallrye-kafka.apicurio.registry.url`
- Optional custom topic mappings
- Your channel names in code

The extension enforces platform standards automatically. Manual configuration will conflict with the extension and cause issues.

## How It All Works Together

**The Extension's Magic:**

1. **Scans your code** during build-time for `@Channel` and `@Incoming` annotations
2. **Generates configuration** automatically based on your channel names
3. **Applies platform standards** (UUID keys, Protobuf values, reliability settings)
4. **Maps channels to topics** using intelligent naming rules
5. **Configures Apicurio** for schema management and evolution

**Developer Experience:**
- Add one dependency
- Set two infrastructure URLs
- Use channel names in code
- Get production-ready Kafka messaging

**No more:** Manual serializer config, connector setup, registry configuration, topic mapping, etc.

**All automatic:** Standards compliance, schema evolution, reliable messaging, proper error handling.
