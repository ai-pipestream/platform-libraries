package ai.pipestream.registration.clients;

import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.platform.registration.EventType;
import ai.pipestream.platform.registration.ModuleRegistrationRequest;
import ai.pipestream.platform.registration.RegistrationEvent;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Client responsible for auto-registering a module with the platform registration service
 * when the application starts (if module.registration.enabled=true).
 */
@ApplicationScoped
@IfBuildProperty(name = "module.registration.enabled", stringValue = "true")
public class ModuleRegistrationClient {

    /**
     * Default constructor for CDI/JAX-RS. Exists to satisfy Javadoc requirement.
     */
    public ModuleRegistrationClient() {
        // no-op
    }

    private static final Logger LOG = Logger.getLogger(ModuleRegistrationClient.class);

    @Inject
    DynamicGrpcClientFactory grpcClientFactory;

    @ConfigProperty(name = "module.registration.module-name")
    String moduleName;

    @ConfigProperty(name = "module.registration.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "module.registration.port")
    int port;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String version;

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    private static final String REGISTRATION_SERVICE = "platform-registration-service";

    void onStart(@Observes StartupEvent ev) {
        LOG.infof("Auto-registering module %s with platform registration service", moduleName);
        registerModule()
            .subscribe().with(
                event -> {
                    LOG.infof("Module registration event: %s - %s", event.getEventType(), event.getMessage());
                    if (event.getEventType() == EventType.COMPLETED) {
                        LOG.infof("Successfully registered module %s with platform", moduleName);
                    } else if (event.getEventType() == EventType.FAILED) {
                        LOG.errorf("Failed to register module %s: %s", moduleName, event.getMessage());
                        if (event.hasErrorDetail()) {
                            LOG.error("Details: " + event.getErrorDetail());
                        }
                    }
                },
                throwable -> LOG.error("Module registration failed", throwable),
                () -> LOG.debug("Module registration stream completed")
            );
    }

    /**
     * Initiates the module registration stream with the platform registration service.
     * @return a stream (Multi) of registration events describing the progress and outcome
     *         of the registration attempt
     */
    public Multi<RegistrationEvent> registerModule() {
        return grpcClientFactory.getPlatformRegistrationClient(REGISTRATION_SERVICE)
            .onItem().transformToMulti(stub -> {
                ModuleRegistrationRequest request = buildRequest();
                LOG.infof("Registering module: %s at %s:%d", moduleName, determineHost(), port);
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

    private ModuleRegistrationRequest buildRequest() {
        return ModuleRegistrationRequest.newBuilder()
            .setModuleName(moduleName)
            .setHost(determineHost())
            .setPort(port)
            .setVersion(version)
            .build();
    }

    private String determineHost() {
        if ("dev".equals(profile) && "localhost".equals(host)) {
            String bridge = System.getenv("DOCKER_BRIDGE_IP");
            return bridge != null && !bridge.isEmpty() ? bridge : "172.17.0.1";
        }
        return host;
    }
}


