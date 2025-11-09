# Pipeline Auto-Registration

This package provides automatic registration of pipeline modules with the central registration service using Stork service discovery.

## How it Works

1. **Service Discovery**: Modules use Stork to discover the registration service via Consul
2. **Automatic Scanning**: The `PipelineAutoRegistrationBean` scans for all beans implementing `PipeStepProcessor` that are annotated with `@PipelineAutoRegister`
3. **Registration**: Each annotated processor is automatically registered with the registration service via gRPC
4. **Health Checking**: The registration service handles all Consul registration and health checking

## Configuration

Modules need to configure Stork to use Consul for service discovery:

```properties
# gRPC client configuration
quarkus.grpc.clients.registration-service.host=registration-service
quarkus.grpc.clients.registration-service.name-resolver=stork

# Stork configuration for Consul
quarkus.stork.registration-service.service-discovery.type=consul
quarkus.stork.registration-service.service-discovery.consul-host=localhost
quarkus.stork.registration-service.service-discovery.consul-port=8500
```

## Usage

Simply annotate your processor with `@PipelineAutoRegister`:

```java
@ApplicationScoped
@PipelineAutoRegister(
    moduleType = "nlp-processor",
    metadata = {"capability=sentiment-analysis", "version=2.0"}
)
public class SentimentAnalyzer implements PipeStepProcessor {
    // ... implementation
}
```

The module will be automatically registered on startup and unregistered on shutdown.

## Benefits

- **No Direct Consul Dependencies**: Modules don't need to manage Consul clients
- **Automatic Discovery**: Stork handles service discovery transparently
- **Centralized Management**: The registration service handles all Consul interactions
- **Clean Separation**: Modules focus on business logic, not infrastructure