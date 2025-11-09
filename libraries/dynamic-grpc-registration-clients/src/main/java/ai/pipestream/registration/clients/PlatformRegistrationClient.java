package ai.pipestream.registration.clients;

import ai.pipestream.common.grpc.GrpcClientFactory;
import ai.pipestream.data.module.ServiceRegistrationMetadata;
import ai.pipestream.platform.registration.*;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client helper for registering services/modules with the platform registration service
 */
@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "service.registration.enabled", stringValue = "true")
public class PlatformRegistrationClient {
    
    /**
     * Default no-args constructor for CDI. Exists to document the default constructor usage.
     */
    public PlatformRegistrationClient() {
        // no-op
    }
    
    private static final Logger LOG = Logger.getLogger(PlatformRegistrationClient.class);
    
    @Inject
    GrpcClientFactory grpcClientFactory;
    
    // Service registration configuration from application.properties
    @ConfigProperty(name = "service.registration.enabled", defaultValue = "false")
    boolean registrationEnabled;
    
    @ConfigProperty(name = "service.registration.service-name", defaultValue = "")
    String serviceName;
    
    @ConfigProperty(name = "service.registration.description", defaultValue = "")
    String description;
    
    @ConfigProperty(name = "service.registration.service-type", defaultValue = "APPLICATION")
    String serviceType;
    
    @ConfigProperty(name = "service.registration.host", defaultValue = "localhost")
    String serviceHost;
    
    @ConfigProperty(name = "service.registration.port", defaultValue = "0")
    int servicePort;
    
    @ConfigProperty(name = "service.registration.capabilities", defaultValue = "")
    String capabilities;
    
    @ConfigProperty(name = "service.registration.tags", defaultValue = "")
    String tags;
    
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;
    
    @ConfigProperty(name = "quarkus.profile")
    String profile;
    
    // Platform registration service name for discovery
    private static final String REGISTRATION_SERVICE = "platform-registration-service";
    
    /**
     * Auto-register on startup if enabled
     */
    void onStart(@Observes StartupEvent ev) {
        if (!registrationEnabled) {
            LOG.info("Service registration disabled");
            return;
        }
        
        LOG.infof("Auto-registering %s with platform registration service", serviceName);
        
        registerService()
            .subscribe().with(
                event -> {
                    LOG.infof("Registration event: %s - %s", event.getEventType(), event.getMessage());
                    
                    if (event.getEventType() == EventType.COMPLETED) {
                        LOG.infof("Successfully registered %s with platform", serviceName);
                    } else if (event.getEventType() == EventType.FAILED) {
                        LOG.errorf("Failed to register %s: %s", serviceName, event.getMessage());
                        if (event.hasErrorDetail()) {
                            LOG.error("Details: " + event.getErrorDetail());
                        }
                    }
                },
                throwable -> LOG.error("Registration failed", throwable),
                () -> LOG.debug("Registration stream completed")
            );
    }
    
    /**
     * Register as a service with the platform.
     * @return a stream (Multi) of registration events indicating progress and outcome of the registration
     */
    public Multi<RegistrationEvent> registerService() {
        return grpcClientFactory.getPlatformRegistrationClient(REGISTRATION_SERVICE)
            .onItem().transformToMulti(stub -> {
                ServiceRegistrationRequest request = buildServiceRequest();
                LOG.infof("Registering service: %s at %s:%d", serviceName, determineHost(), servicePort);
                return stub.registerService(request);
            })
            .onFailure().recoverWithMulti(throwable -> {
                LOG.error("Failed to connect to registration service", throwable);
                return Multi.createFrom().item(RegistrationEvent.newBuilder()
                    .setEventType(EventType.FAILED)
                    .setMessage("Failed to connect to registration service")
                    .setErrorDetail(throwable.getMessage())
                    .build());
            });
    }
    
    /**
     * Register as a module with the platform.
     * This would be used by document processing modules.
     * @param metadata optional metadata describing the module's capabilities and other attributes
     * @return a stream (Multi) of registration events indicating progress and outcome of the registration
     */
    public Multi<RegistrationEvent> registerModule(ServiceRegistrationMetadata metadata) {
        return grpcClientFactory.getPlatformRegistrationClient(REGISTRATION_SERVICE)
            .onItem().transformToMulti(stub -> {
                ModuleRegistrationRequest request = buildModuleRequest(metadata);
                LOG.infof("Registering module: %s at %s:%d", serviceName, determineHost(), servicePort);
                return stub.registerModule(request);
            })
            .onFailure().recoverWithMulti(throwable -> {
                LOG.error("Failed to connect to registration service", throwable);
                return Multi.createFrom().item(RegistrationEvent.newBuilder()
                    .setEventType(EventType.FAILED)
                    .setMessage("Failed to connect to registration service")
                    .setErrorDetail(throwable.getMessage())
                    .build());
            });
    }
    
    /**
     * Unregister from the platform.
     * @return the result of the unregister attempt from the platform registration service
     */
    public Uni<UnregisterResponse> unregister() {
        return grpcClientFactory.getPlatformRegistrationClient(REGISTRATION_SERVICE)
            .onItem().transformToUni(stub -> {
                UnregisterRequest request = UnregisterRequest.newBuilder()
                    .setServiceName(serviceName)
                    .setHost(determineHost())
                    .setPort(servicePort)
                    .build();
                
                LOG.infof("Unregistering service: %s", serviceName);
                return stub.unregisterService(request);
            });
    }
    
    /**
     * Build service registration request from configuration
     */
    private ServiceRegistrationRequest buildServiceRequest() {
        ServiceRegistrationRequest.Builder builder = ServiceRegistrationRequest.newBuilder()
            .setServiceName(serviceName)
            .setHost(determineHost())
            .setPort(servicePort)
            .setVersion(version);
        
        // Add metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("description", description);
        metadata.put("service-type", serviceType);
        metadata.put("profile", profile);
        builder.putAllMetadata(metadata);
        
        // Add capabilities
        if (!capabilities.isEmpty()) {
            Arrays.stream(capabilities.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(builder::addCapabilities);
        }
        
        // Add tags
        if (!tags.isEmpty()) {
            Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(builder::addTags);
        }
        
        return builder.build();
    }
    
    /**
     * Build module registration request
     */
    private ModuleRegistrationRequest buildModuleRequest(ServiceRegistrationMetadata metadata) {
        ModuleRegistrationRequest.Builder builder = ModuleRegistrationRequest.newBuilder()
            .setModuleName(serviceName)
            .setHost(determineHost())
            .setPort(servicePort)
            .setVersion(version);
        
        // Add module metadata if provided
        if (metadata != null) {
            builder.setServiceRegistrationMetadata(metadata);
        }
        
        // Add additional metadata
        Map<String, String> additionalMetadata = new HashMap<>();
        additionalMetadata.put("description", description);
        additionalMetadata.put("profile", profile);
        builder.putAllMetadata(additionalMetadata);
        
        return builder.build();
    }
    
    /**
     * Determine the host to register with
     */
    private String determineHost() {
        // Check for override environment variable
        String envHost = System.getenv("SERVICE_REGISTRATION_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            return envHost;
        }
        // Use configured host, but override for Docker dev environments
        if ("dev".equals(profile) && "localhost".equals(serviceHost)) {
            // For dev mode, use Docker bridge IP so Consul can reach us
            return Optional.ofNullable(System.getenv("DOCKER_BRIDGE_IP"))
                    .orElse("172.17.0.1");
        }
        return serviceHost;
    }
}
