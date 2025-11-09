package ai.pipestream.common.util;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing Protocol Buffer descriptors.
 * Provides lookup capabilities for descriptors by type name and caching.
 */
public class DescriptorRegistry {

    private final Map<String, Descriptor> descriptorsByFullName = new ConcurrentHashMap<>();
    private final Map<String, Descriptor> descriptorsBySimpleName = new ConcurrentHashMap<>();

    /**
     * Creates a new empty DescriptorRegistry.
     */
    public DescriptorRegistry() {
        // Register common well-known types by default
        registerWellKnownTypes();
    }

    /**
     * Registers well-known Google protobuf types.
     */
    private void registerWellKnownTypes() {
        try {
            // Register google.protobuf.Struct
            register(com.google.protobuf.Struct.getDescriptor());
            register(com.google.protobuf.Value.getDescriptor());
            register(com.google.protobuf.ListValue.getDescriptor());
            register(com.google.protobuf.Timestamp.getDescriptor());
            register(com.google.protobuf.Duration.getDescriptor());
            register(com.google.protobuf.Any.getDescriptor());
        } catch (Exception e) {
            // Ignore if registration fails
        }
    }

    /**
     * Registers a descriptor in the registry.
     *
     * @param descriptor The descriptor to register
     */
    public void register(Descriptor descriptor) {
        descriptorsByFullName.put(descriptor.getFullName(), descriptor);
        descriptorsBySimpleName.put(descriptor.getName(), descriptor);
    }

    /**
     * Registers all message types from a file descriptor.
     *
     * @param fileDescriptor The file descriptor to register
     */
    public void registerFile(FileDescriptor fileDescriptor) {
        for (Descriptor messageType : fileDescriptor.getMessageTypes()) {
            register(messageType);
            // Also register nested types
            registerNestedTypes(messageType);
        }
    }

    /**
     * Recursively registers nested message types.
     *
     * @param descriptor The parent descriptor
     */
    private void registerNestedTypes(Descriptor descriptor) {
        for (Descriptor nested : descriptor.getNestedTypes()) {
            register(nested);
            registerNestedTypes(nested);
        }
    }

    /**
     * Registers a descriptor from a message instance.
     *
     * @param message The message whose descriptor should be registered
     */
    public void registerFromMessage(Message message) {
        register(message.getDescriptorForType());
    }

    /**
     * Finds a descriptor by its full name.
     *
     * @param fullName The full name (e.g., "ai.pipestream.data.v1.SearchMetadata")
     * @return The descriptor, or null if not found
     */
    public Descriptor findDescriptorByFullName(String fullName) {
        return descriptorsByFullName.get(fullName);
    }

    /**
     * Finds a descriptor by its simple name.
     * Note: This may return unexpected results if multiple types have the same simple name.
     *
     * @param simpleName The simple name (e.g., "SearchMetadata")
     * @return The descriptor, or null if not found
     */
    public Descriptor findDescriptorBySimpleName(String simpleName) {
        return descriptorsBySimpleName.get(simpleName);
    }

    /**
     * Finds a descriptor by either full or simple name.
     *
     * @param name The name to search for
     * @return The descriptor, or null if not found
     */
    public Descriptor findDescriptor(String name) {
        Descriptor descriptor = findDescriptorByFullName(name);
        if (descriptor == null) {
            descriptor = findDescriptorBySimpleName(name);
        }
        return descriptor;
    }

    /**
     * Checks if a descriptor is registered.
     *
     * @param fullName The full name of the type
     * @return true if the descriptor is registered
     */
    public boolean isRegistered(String fullName) {
        return descriptorsByFullName.containsKey(fullName);
    }

    /**
     * Clears all registered descriptors except well-known types.
     */
    public void clear() {
        descriptorsByFullName.clear();
        descriptorsBySimpleName.clear();
        registerWellKnownTypes();
    }

    /**
     * Gets the number of registered descriptors.
     *
     * @return The count of registered descriptors
     */
    public int size() {
        return descriptorsByFullName.size();
    }

    /**
     * Creates a builder for fluently registering multiple descriptors.
     *
     * @return A new RegistryBuilder
     */
    public static RegistryBuilder builder() {
        return new RegistryBuilder();
    }

    /**
     * Builder for fluently constructing a DescriptorRegistry.
     */
    public static class RegistryBuilder {
        private final DescriptorRegistry registry = new DescriptorRegistry();

        /**
         * Registers a descriptor.
         *
         * @param descriptor The descriptor to register
         * @return This builder
         */
        public RegistryBuilder register(Descriptor descriptor) {
            registry.register(descriptor);
            return this;
        }

        /**
         * Registers a file descriptor.
         *
         * @param fileDescriptor The file descriptor to register
         * @return This builder
         */
        public RegistryBuilder registerFile(FileDescriptor fileDescriptor) {
            registry.registerFile(fileDescriptor);
            return this;
        }

        /**
         * Registers a descriptor from a message instance.
         *
         * @param message The message whose descriptor should be registered
         * @return This builder
         */
        public RegistryBuilder registerFromMessage(Message message) {
            registry.registerFromMessage(message);
            return this;
        }

        /**
         * Builds the DescriptorRegistry.
         *
         * @return The constructed registry
         */
        public DescriptorRegistry build() {
            return registry;
        }
    }
}
