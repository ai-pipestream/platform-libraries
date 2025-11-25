package ai.pipestream.kafka.admin;

/**
 * Unchecked exception thrown when automatic Kafka topic provisioning fails at application startup.
 *
 * <p>This exception is used by {@link TopicProvisioner} to fail fast if Kafka is configured and the
 * application cannot verify or create required topics. Failing fast prevents the service from
 * running in a partially configured state where message production or consumption would later fail
 * at runtime.</p>
 */
public class TopicProvisioningException extends RuntimeException {

    /**
     * Creates a new TopicProvisioningException with a message.
     *
     * @param message explanation of the provisioning failure
     */
    public TopicProvisioningException(String message) {
        super(message);
    }

    /**
     * Creates a new TopicProvisioningException with a message and a root cause.
     *
     * @param message explanation of the provisioning failure
     * @param cause the underlying cause (e.g., Kafka connection errors)
     */
    public TopicProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
