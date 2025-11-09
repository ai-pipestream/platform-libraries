package ai.pipestream.common.service;

import ai.pipestream.common.interceptor.ProcessingBufferInterceptor;
import ai.pipestream.common.util.ProcessingBuffer;
import ai.pipestream.data.v1.PipeDoc;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;

/**
 * CDI bean that provides convenient access to processing buffers managed by the interceptor.
 * This follows the Quarkus pattern of using static methods on interceptors for data access.
 * 
 * This manager acts as a CDI-injectable facade over the static methods, making it easier
 * to use in tests and other CDI beans while following established Quarkus patterns.
 */
@ApplicationScoped
public class ProcessingBufferManager {

    private static final Logger LOG = Logger.getLogger(ProcessingBufferManager.class);

    /**
     * Gets the buffer for a specific method key.
     */
    public ProcessingBuffer<PipeDoc> getBuffer(String methodKey) {
        return ProcessingBufferInterceptor.getBuffer(methodKey);
    }

    /**
     * Gets the buffer for a specific method.
     */
    public ProcessingBuffer<PipeDoc> getBuffer(String className, String methodName) {
        return ProcessingBufferInterceptor.getBuffer(className, methodName);
    }

    /**
     * Gets all active buffer keys.
     */
    public Set<String> getActiveBufferKeys() {
        return ProcessingBufferInterceptor.getActiveBufferKeys();
    }

    /**
     * Gets the total number of captured documents across all buffers.
     */
    public int getTotalCapturedDocuments() {
        return ProcessingBufferInterceptor.getTotalCapturedDocuments();
    }

    /**
     * Gets buffer statistics for monitoring.
     */
    public Map<String, Integer> getBufferStatistics() {
        return ProcessingBufferInterceptor.getBufferStatistics();
    }

    /**
     * Saves all buffers to disk.
     */
    public void saveAllBuffers() {
        ProcessingBufferInterceptor.saveAllBuffers();
    }

    /**
     * Saves a specific buffer to disk.
     */
    public boolean saveBuffer(String methodKey) {
        return ProcessingBufferInterceptor.saveBuffer(methodKey);
    }

    /**
     * Clears all buffers.
     */
    public void clearAllBuffers() {
        ProcessingBufferInterceptor.clearAllBuffers();
    }
}