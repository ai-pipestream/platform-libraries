package ai.pipestream.api.events;

import java.util.Set;

/**
 * A CDI event that is fired when the list of registered pipeline services changes.
 * This event serves as a decoupling mechanism between the service that manages
 * registrations (e.g., from Consul) and any components that need to validate
 * against the list of available services.
 */
public final class ServiceListUpdatedEvent {

    private final Set<String> serviceNames;

    /**
     * Constructs a new event.
     *
     * @param serviceNames The complete, updated set of registered service names.
     *                     The provided set should be immutable or a defensive copy.
     */
    public ServiceListUpdatedEvent(Set<String> serviceNames) {
        this.serviceNames = serviceNames;
    }

    /**
     * @return The complete set of registered service names.
     */
    public Set<String> getServiceNames() {
        return serviceNames;
    }
}
