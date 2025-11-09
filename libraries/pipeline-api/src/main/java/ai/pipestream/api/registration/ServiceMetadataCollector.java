package ai.pipestream.api.registration;

import ai.pipestream.api.annotation.GrpcServiceRegistration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects comprehensive metadata for service registration from annotations,
 * configuration, and runtime environment.
 */
@ApplicationScoped
public class ServiceMetadataCollector {
    
    private static final Logger LOG = Logger.getLogger(ServiceMetadataCollector.class);
    
    @Inject
    IconResourceLoader iconLoader;
    
    private final Config config;
    
    public ServiceMetadataCollector() {
        this.config = ConfigProvider.getConfig();
    }
    
    /**
     * Collect comprehensive service metadata from annotation and configuration
     */
    public ServiceMetadata collectServiceMetadata(Class<?> serviceClass, 
                                                GrpcServiceRegistration annotation) {
        String serviceName = determineServiceName(serviceClass, annotation);
        
        return ServiceMetadata.builder()
            .serviceName(serviceName)
            .serviceId(generateServiceId(serviceName))
            .serviceType(annotation.serviceType())
            .description(resolveConfigValue(annotation.description(), serviceName + ".description"))
            .version(resolveConfigValue(annotation.version(), serviceName + ".version"))
            .host(determineHost(serviceName))
            .port(determinePort(serviceName))
            .grpcPort(determineGrpcPort(serviceName))
            .iconSvgBase64(loadServiceIcon(annotation.iconPath(), serviceName))
            .capabilities(collectCapabilities(annotation, serviceName))
            .tags(collectTags(annotation, serviceName))
            .metadata(collectCustomMetadata(annotation, serviceName, serviceClass))
            .healthChecks(collectHealthChecks(annotation, serviceName))
            .registeredAt(Instant.now())
            .registeredBy(determineRegisteredBy())
            .build();
    }
    
    /**
     * Determine the service name from annotation or class
     */
    private String determineServiceName(Class<?> serviceClass, GrpcServiceRegistration annotation) {
        if (!annotation.serviceName().isEmpty()) {
            return resolveConfigValue(annotation.serviceName(), "service.name");
        }
        
        // Try from config first
        Optional<String> configName = config.getOptionalValue("quarkus.application.name", String.class);
        if (configName.isPresent()) {
            return configName.get();
        }
        
        // Fall back to class name
        String className = serviceClass.getSimpleName();
        if (className.endsWith("Impl")) {
            className = className.substring(0, className.length() - 4);
        }
        if (className.endsWith("Service")) {
            className = className.substring(0, className.length() - 7);
        }
        
        return className.toLowerCase().replaceAll("([a-z])([A-Z])", "$1-$2");
    }
    
    /**
     * Generate unique service ID
     */
    private String generateServiceId(String serviceName) {
        String hostname = getHostname();
        long timestamp = System.currentTimeMillis();
        return String.format("%s-%s-%d", serviceName, hostname, timestamp);
    }
    
    /**
     * Determine service host
     */
    private String determineHost(String serviceName) {
        // Check service-specific host config
        String hostKey = String.format("service.registration.%s.host", serviceName);
        Optional<String> serviceHost = config.getOptionalValue(hostKey, String.class);
        if (serviceHost.isPresent()) {
            return serviceHost.get();
        }
        
        // Check general registration host
        Optional<String> registrationHost = config.getOptionalValue("service.registration.host", String.class);
        if (registrationHost.isPresent()) {
            return registrationHost.get();
        }
        
        // Check Quarkus HTTP host (but not 0.0.0.0)
        Optional<String> httpHost = config.getOptionalValue("quarkus.http.host", String.class);
        if (httpHost.isPresent() && !"0.0.0.0".equals(httpHost.get())) {
            return httpHost.get();
        }
        
        // Check environment variables
        String envHost = System.getenv("SERVICE_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            return envHost;
        }
        
        // Fall back to hostname
        return getHostname();
    }
    
    /**
     * Determine service port
     */
    private int determinePort(String serviceName) {
        // Service-specific port
        String portKey = String.format("service.registration.%s.port", serviceName);
        Optional<Integer> servicePort = config.getOptionalValue(portKey, Integer.class);
        if (servicePort.isPresent()) {
            return servicePort.get();
        }
        
        // Default to HTTP port
        return config.getOptionalValue("quarkus.http.port", Integer.class).orElse(8080);
    }
    
    /**
     * Determine gRPC port
     */
    private int determineGrpcPort(String serviceName) {
        // Service-specific gRPC port
        String grpcPortKey = String.format("service.registration.%s.grpc-port", serviceName);
        Optional<Integer> serviceGrpcPort = config.getOptionalValue(grpcPortKey, Integer.class);
        if (serviceGrpcPort.isPresent()) {
            return serviceGrpcPort.get();
        }
        
        // Check if using separate gRPC server
        boolean separateGrpcServer = config.getOptionalValue("quarkus.grpc.server.use-separate-server", Boolean.class)
            .orElse(true);
        
        if (separateGrpcServer) {
            return config.getOptionalValue("quarkus.grpc.server.port", Integer.class).orElse(9000);
        } else {
            // Using unified server - gRPC port same as HTTP port
            return determinePort(serviceName);
        }
    }
    
    /**
     * Load service icon
     */
    private String loadServiceIcon(String iconPath, String serviceName) {
        // Try annotation path first
        if (!iconPath.isEmpty()) {
            String resolvedPath = resolveConfigValue(iconPath, serviceName + ".icon-path");
            Optional<String> icon = iconLoader.loadIcon(resolvedPath);
            if (icon.isPresent()) {
                return icon.get();
            }
        }
        
        // Try service-specific config
        String iconConfigKey = String.format("service.registration.%s.icon-path", serviceName);
        Optional<String> configIconPath = config.getOptionalValue(iconConfigKey, String.class);
        if (configIconPath.isPresent()) {
            Optional<String> icon = iconLoader.loadIcon(configIconPath.get());
            if (icon.isPresent()) {
                return icon.get();
            }
        }
        
        // Fall back to default icon
        return iconLoader.getDefaultServiceIcon();
    }
    
    /**
     * Collect service capabilities
     */
    private Set<String> collectCapabilities(GrpcServiceRegistration annotation, String serviceName) {
        Set<String> capabilities = new HashSet<>();
        
        // From annotation
        capabilities.addAll(Arrays.asList(annotation.capabilities()));
        
        // From configuration
        String capabilitiesKey = String.format("service.registration.%s.capabilities", serviceName);
        Optional<String> configCapabilities = config.getOptionalValue(capabilitiesKey, String.class);
        if (configCapabilities.isPresent()) {
            String[] caps = configCapabilities.get().split(",");
            for (String cap : caps) {
                capabilities.add(cap.trim());
            }
        }
        
        return capabilities;
    }
    
    /**
     * Collect service tags
     */
    private Set<String> collectTags(GrpcServiceRegistration annotation, String serviceName) {
        Set<String> tags = new HashSet<>();
        
        // From annotation
        tags.addAll(Arrays.asList(annotation.tags()));
        
        // From configuration
        String tagsKey = String.format("service.registration.%s.tags", serviceName);
        Optional<String> configTags = config.getOptionalValue(tagsKey, String.class);
        if (configTags.isPresent()) {
            String[] tagArray = configTags.get().split(",");
            for (String tag : tagArray) {
                tags.add(tag.trim());
            }
        }
        
        // Add default tags
        tags.add("grpc");
        tags.add("service");
        tags.add("version:" + resolveConfigValue(annotation.version(), serviceName + ".version"));
        
        return tags;
    }
    
    /**
     * Collect custom metadata
     */
    private Map<String, String> collectCustomMetadata(GrpcServiceRegistration annotation, 
                                                    String serviceName, 
                                                    Class<?> serviceClass) {
        Map<String, String> metadata = new HashMap<>();
        
        // Runtime metadata
        metadata.put("service-class", serviceClass.getName());
        metadata.put("java-version", System.getProperty("java.version"));
        metadata.put("startup-time", Instant.now().toString());
        
        // Quarkus metadata
        config.getOptionalValue("quarkus.application.version", String.class)
            .ifPresent(v -> metadata.put("quarkus-version", v));
        
        // From annotation
        for (String meta : annotation.metadata()) {
            String[] parts = meta.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = resolveConfigValue(parts[1].trim(), serviceName + ".metadata." + key);
                metadata.put(key, value);
            }
        }
        
        // From configuration (service.registration.{serviceName}.metadata.*)
        String metadataPrefix = String.format("service.registration.%s.metadata.", serviceName);
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(metadataPrefix)) {
                String key = propertyName.substring(metadataPrefix.length());
                String value = config.getValue(propertyName, String.class);
                metadata.put(key, value);
            }
        }
        
        // Infer capabilities from gRPC methods
        Set<String> grpcMethods = extractGrpcMethods(serviceClass);
        if (!grpcMethods.isEmpty()) {
            metadata.put("grpc-methods", String.join(",", grpcMethods));
            metadata.put("method-count", String.valueOf(grpcMethods.size()));
        }
        
        return metadata;
    }
    
    /**
     * Extract gRPC method names from service class
     */
    private Set<String> extractGrpcMethods(Class<?> serviceClass) {
        Set<String> methods = new HashSet<>();
        
        for (Method method : serviceClass.getDeclaredMethods()) {
            // Skip synthetic and bridge methods
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            
            // gRPC methods typically return Uni or have specific parameter patterns
            if (method.getReturnType().getName().contains("Uni") ||
                method.getParameterCount() > 0) {
                methods.add(method.getName());
            }
        }
        
        return methods;
    }
    
    /**
     * Collect health check configurations
     */
    private List<HealthCheckConfig> collectHealthChecks(GrpcServiceRegistration annotation, 
                                                       String serviceName) {
        List<HealthCheckConfig> healthChecks = new ArrayList<>();
        
        for (GrpcServiceRegistration.HealthCheckType type : annotation.healthChecks()) {
            switch (type) {
                case HTTP:
                    healthChecks.add(createHttpHealthCheck(serviceName));
                    break;
                case GRPC:
                    healthChecks.add(createGrpcHealthCheck(serviceName));
                    break;
                case TCP:
                    healthChecks.add(createTcpHealthCheck(serviceName));
                    break;
            }
        }
        
        return healthChecks;
    }
    
    private HealthCheckConfig createHttpHealthCheck(String serviceName) {
        String endpoint = config.getOptionalValue(
            String.format("service.registration.%s.health-check.http.endpoint", serviceName), 
            String.class
        ).orElse("/q/health");
        
        String interval = config.getOptionalValue(
            String.format("service.registration.%s.health-check.http.interval", serviceName), 
            String.class
        ).orElse("10s");
        
        return HealthCheckConfig.builder()
            .type(GrpcServiceRegistration.HealthCheckType.HTTP)
            .endpoint(endpoint)
            .interval(interval)
            .timeout("5s")
            .deregisterAfter("1m")
            .build();
    }
    
    private HealthCheckConfig createGrpcHealthCheck(String serviceName) {
        String interval = config.getOptionalValue(
            String.format("service.registration.%s.health-check.grpc.interval", serviceName), 
            String.class
        ).orElse("10s");
        
        return HealthCheckConfig.builder()
            .type(GrpcServiceRegistration.HealthCheckType.GRPC)
            .endpoint("") // gRPC health check doesn't need endpoint path
            .interval(interval)
            .timeout("5s")
            .deregisterAfter("1m")
            .build();
    }
    
    private HealthCheckConfig createTcpHealthCheck(String serviceName) {
        String interval = config.getOptionalValue(
            String.format("service.registration.%s.health-check.tcp.interval", serviceName), 
            String.class
        ).orElse("10s");
        
        return HealthCheckConfig.builder()
            .type(GrpcServiceRegistration.HealthCheckType.TCP)
            .endpoint("") // TCP check doesn't need endpoint path
            .interval(interval)
            .timeout("3s")
            .deregisterAfter("30s")
            .build();
    }
    
    /**
     * Resolve configuration values with property placeholder support
     */
    private String resolveConfigValue(String value, String fallbackKey) {
        if (value == null || value.isEmpty()) {
            return config.getOptionalValue(fallbackKey, String.class).orElse("");
        }
        
        // Handle property placeholders like ${property:defaultValue}
        if (value.startsWith("${") && value.endsWith("}")) {
            String placeholder = value.substring(2, value.length() - 1);
            String[] parts = placeholder.split(":", 2);
            String propertyName = parts[0];
            String defaultValue = parts.length > 1 ? parts[1] : "";
            
            return config.getOptionalValue(propertyName, String.class).orElse(defaultValue);
        }
        
        return value;
    }
    
    private String getHostname() {
        try {
            // Check environment variables first
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
            
            // Fall back to InetAddress
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            LOG.warn("Could not determine hostname, using 'localhost'", e);
            return "localhost";
        }
    }
    
    private String determineRegisteredBy() {
        return String.format("service-registration-processor@%s", getHostname());
    }
    
    // Data classes for structured metadata
    
    public static class ServiceMetadata {
        private final String serviceName;
        private final String serviceId;
        private final GrpcServiceRegistration.ServiceType serviceType;
        private final String description;
        private final String version;
        private final String host;
        private final int port;
        private final int grpcPort;
        private final String iconSvgBase64;
        private final Set<String> capabilities;
        private final Set<String> tags;
        private final Map<String, String> metadata;
        private final List<HealthCheckConfig> healthChecks;
        private final Instant registeredAt;
        private final String registeredBy;
        
        private ServiceMetadata(Builder builder) {
            this.serviceName = builder.serviceName;
            this.serviceId = builder.serviceId;
            this.serviceType = builder.serviceType;
            this.description = builder.description;
            this.version = builder.version;
            this.host = builder.host;
            this.port = builder.port;
            this.grpcPort = builder.grpcPort;
            this.iconSvgBase64 = builder.iconSvgBase64;
            this.capabilities = Set.copyOf(builder.capabilities);
            this.tags = Set.copyOf(builder.tags);
            this.metadata = Map.copyOf(builder.metadata);
            this.healthChecks = List.copyOf(builder.healthChecks);
            this.registeredAt = builder.registeredAt;
            this.registeredBy = builder.registeredBy;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getServiceName() { return serviceName; }
        public String getServiceId() { return serviceId; }
        public GrpcServiceRegistration.ServiceType getServiceType() { return serviceType; }
        public String getDescription() { return description; }
        public String getVersion() { return version; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getGrpcPort() { return grpcPort; }
        public String getIconSvgBase64() { return iconSvgBase64; }
        public Set<String> getCapabilities() { return capabilities; }
        public Set<String> getTags() { return tags; }
        public Map<String, String> getMetadata() { return metadata; }
        public List<HealthCheckConfig> getHealthChecks() { return healthChecks; }
        public Instant getRegisteredAt() { return registeredAt; }
        public String getRegisteredBy() { return registeredBy; }
        
        public static class Builder {
            private String serviceName;
            private String serviceId;
            private GrpcServiceRegistration.ServiceType serviceType;
            private String description = "";
            private String version = "1.0.0";
            private String host;
            private int port;
            private int grpcPort;
            private String iconSvgBase64 = "";
            private Set<String> capabilities = new HashSet<>();
            private Set<String> tags = new HashSet<>();
            private Map<String, String> metadata = new HashMap<>();
            private List<HealthCheckConfig> healthChecks = new ArrayList<>();
            private Instant registeredAt;
            private String registeredBy;
            
            public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
            public Builder serviceId(String serviceId) { this.serviceId = serviceId; return this; }
            public Builder serviceType(GrpcServiceRegistration.ServiceType serviceType) { this.serviceType = serviceType; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder version(String version) { this.version = version; return this; }
            public Builder host(String host) { this.host = host; return this; }
            public Builder port(int port) { this.port = port; return this; }
            public Builder grpcPort(int grpcPort) { this.grpcPort = grpcPort; return this; }
            public Builder iconSvgBase64(String iconSvgBase64) { this.iconSvgBase64 = iconSvgBase64; return this; }
            public Builder capabilities(Set<String> capabilities) { this.capabilities = capabilities; return this; }
            public Builder tags(Set<String> tags) { this.tags = tags; return this; }
            public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
            public Builder healthChecks(List<HealthCheckConfig> healthChecks) { this.healthChecks = healthChecks; return this; }
            public Builder registeredAt(Instant registeredAt) { this.registeredAt = registeredAt; return this; }
            public Builder registeredBy(String registeredBy) { this.registeredBy = registeredBy; return this; }
            
            public ServiceMetadata build() {
                return new ServiceMetadata(this);
            }
        }
    }
    
    public static class HealthCheckConfig {
        private final GrpcServiceRegistration.HealthCheckType type;
        private final String endpoint;
        private final String interval;
        private final String timeout;
        private final String deregisterAfter;
        
        private HealthCheckConfig(Builder builder) {
            this.type = builder.type;
            this.endpoint = builder.endpoint;
            this.interval = builder.interval;
            this.timeout = builder.timeout;
            this.deregisterAfter = builder.deregisterAfter;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public GrpcServiceRegistration.HealthCheckType getType() { return type; }
        public String getEndpoint() { return endpoint; }
        public String getInterval() { return interval; }
        public String getTimeout() { return timeout; }
        public String getDeregisterAfter() { return deregisterAfter; }
        
        public static class Builder {
            private GrpcServiceRegistration.HealthCheckType type;
            private String endpoint;
            private String interval;
            private String timeout;
            private String deregisterAfter;
            
            public Builder type(GrpcServiceRegistration.HealthCheckType type) { this.type = type; return this; }
            public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
            public Builder interval(String interval) { this.interval = interval; return this; }
            public Builder timeout(String timeout) { this.timeout = timeout; return this; }
            public Builder deregisterAfter(String deregisterAfter) { this.deregisterAfter = deregisterAfter; return this; }
            
            public HealthCheckConfig build() {
                return new HealthCheckConfig(this);
            }
        }
    }
}
