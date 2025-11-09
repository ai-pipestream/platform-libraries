package ai.pipestream.dynamic.grpc;

import ai.pipestream.dynamic.grpc.client.discovery.ServiceDiscovery;
import io.quarkus.test.Mock;
import io.smallrye.mutiny.Uni;
import io.smallrye.stork.api.ServiceInstance;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.time.Duration;

/**
 * Mock implementation of ServiceDiscovery for testing.
 */
@Mock
@ApplicationScoped
public class MockServiceDiscovery implements ServiceDiscovery {
    
    private final Map<String, ServiceInstance> services = new ConcurrentHashMap<>();
    private final Map<String, Supplier<List<ServiceInstance>>> serviceSuppliers = new ConcurrentHashMap<>();
    private static int testGrpcPort = 0;
    private long delayMillis = 0;
    
    /**
     * Add a service instance for testing.
     */
    public void addService(String serviceName, String host, int port) {
        services.put(serviceName, new TestServiceInstance(serviceName, host, port));
    }
    
    /**
     * Clear all services.
     */
    public void clear() {
        services.clear();
        serviceSuppliers.clear();
    }
    
    /**
     * Set the test gRPC port for echo-test service.
     */
    public static void setTestGrpcPort(int port) {
        testGrpcPort = port;
    }
    
    /**
     * Set a delay for service discovery (to test timeouts).
     */
    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }
    
    /**
     * Set a service supplier for advanced testing scenarios.
     */
    public void setServiceSupplier(String serviceName, Supplier<List<ServiceInstance>> supplier) {
        serviceSuppliers.put(serviceName, supplier);
    }
    
    @Override
    public Uni<ServiceInstance> discoverService(String serviceName) {
        // Add delay if configured (for timeout testing)
        if (delayMillis > 0) {
            return Uni.createFrom().item(serviceName)
                .onItem().delayIt().by(Duration.ofMillis(delayMillis))
                .onItem().transformToUni(this::doDiscovery);
        }
        return doDiscovery(serviceName);
    }
    
    private Uni<ServiceInstance> doDiscovery(String serviceName) {
        // Check if there's a supplier for this service (for advanced testing)
        if (serviceSuppliers.containsKey(serviceName)) {
            return Uni.createFrom().deferred(() -> {
                try {
                    var instances = serviceSuppliers.get(serviceName).get();
                    if (instances.isEmpty()) {
                        return Uni.createFrom().failure(new RuntimeException("Service not found: " + serviceName));
                    }
                    // Return first instance for simplicity
                    return Uni.createFrom().item(instances.get(0));
                } catch (Exception e) {
                    return Uni.createFrom().failure(e);
                }
            });
        }
        
        // Check explicit services
        ServiceInstance instance = services.get(serviceName);
        if (instance != null) {
            return Uni.createFrom().item(instance);
        }
        
        // Return default test instances for common test service names
        return switch (serviceName) {
            case "echo" -> Uni.createFrom().item(new TestServiceInstance("echo", "localhost", 49091));
            case "test-module" -> Uni.createFrom().item(new TestServiceInstance("test-module", "localhost", 49092));
            case "mutiny-test" -> Uni.createFrom().item(new TestServiceInstance("mutiny-test", "localhost", 49093));
            case "echo-test" -> Uni.createFrom().item(new TestServiceInstance("echo-test", "localhost", testGrpcPort != 0 ? testGrpcPort : 49094));
            default -> Uni.createFrom().failure(new RuntimeException("Service not found: " + serviceName));
        };
    }
    
    @Override
    public Uni<List<ServiceInstance>> discoverAllInstances(String serviceName) {
        ServiceInstance instance = services.get(serviceName);
        if (instance != null) {
            return Uni.createFrom().item(List.of(instance));
        }
        return Uni.createFrom().item(List.of());
    }
    
    /**
     * Simple test implementation of ServiceInstance.
     */
    private static class TestServiceInstance implements ServiceInstance {
        private final String serviceName;
        private final String host;
        private final int port;
        
        TestServiceInstance(String serviceName, String host, int port) {
            this.serviceName = serviceName;
            this.host = host;
            this.port = port;
        }
        
        @Override
        public long getId() {
            return serviceName.hashCode();
        }
        
        @Override
        public String getHost() {
            return host;
        }
        
        @Override
        public int getPort() {
            return port;
        }
        
        @Override
        public boolean isSecure() {
            return false;
        }
        
        @Override
        public Optional<String> getPath() {
            return Optional.empty();
        }
        
        @Override
        public Map<String, String> getLabels() {
            return Map.of("service", serviceName);
        }
        
        @Override
        public io.smallrye.stork.api.Metadata<? extends io.smallrye.stork.api.MetadataKey> getMetadata() {
            return io.smallrye.stork.api.Metadata.empty();
        }
    }
}