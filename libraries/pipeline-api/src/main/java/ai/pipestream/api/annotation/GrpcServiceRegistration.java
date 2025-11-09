package ai.pipestream.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic gRPC service registration with enhanced metadata support.
 * This is specifically for application services (not pipeline modules).
 * <p>
 * Services annotated with this will be automatically registered with the registration
 * service on startup, including rich metadata, capabilities, and SVG icons.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GrpcServiceRegistration {
    
    /**
     * Service name for registration. Defaults to the class simple name.
     */
    String serviceName() default "";
    
    /**
     * Service type - distinguishes applications from pipeline modules
     */
    ServiceType serviceType() default ServiceType.APPLICATION;
    
    /**
     * Human-readable description of the service
     */
    String description() default "";
    
    /**
     * Service version. Supports property placeholders like ${service.version:1.0.0}
     */
    String version() default "${service.version:1.0.0}";
    
    /**
     * Path to SVG icon for UI display. Supports:
     * - classpath:/icons/service.svg (bundled resource)
     * - /absolute/path/to/icon.svg (file system)
     * - https://cdn.example.com/icon.svg (URL)
     * - data:image/svg+xml;base64,... (inline base64)
     */
    String iconPath() default "";
    
    /**
     * Service capabilities - what this service can do
     */
    String[] capabilities() default {};
    
    /**
     * Health check types to register
     */
    HealthCheckType[] healthChecks() default {HealthCheckType.HTTP, HealthCheckType.GRPC};
    
    /**
     * Additional metadata as key=value pairs
     */
    String[] metadata() default {};
    
    /**
     * Whether registration is enabled. Supports property placeholders.
     */
    String enabled() default "${service.registration.enabled:true}";
    
    /**
     * Tags for service discovery
     */
    String[] tags() default {};
    
    public enum ServiceType {
        APPLICATION,    // Standalone services like mapping-service
        MODULE,         // Pipeline processing modules (use @PipelineAutoRegister instead)
        INFRASTRUCTURE  // Core infrastructure services
    }
    
    public enum HealthCheckType {
        HTTP,   // HTTP health check endpoint
        GRPC,   // gRPC health check
        TCP     // Simple TCP connection check
    }
}
