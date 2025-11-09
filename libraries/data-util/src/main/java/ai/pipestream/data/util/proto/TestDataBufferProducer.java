package ai.pipestream.data.util.proto;

import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.PipeStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Producer for TestDataBuffer instances.
 * This class creates and configures TestDataBuffer instances for injection.
 * It replaces the ProcessingBuffer producer from commons:util to avoid circular dependencies.
 */
@ApplicationScoped
public class TestDataBufferProducer {

    /**
     * Default constructor for TestDataBufferProducer.
     * Creates a new instance that can produce TestDataBuffer instances.
     */
    public TestDataBufferProducer() {
        // Default constructor
    }

    private static final int DEFAULT_BUFFER_SIZE = 100;
    private static final String OUTPUT_DIRECTORY = "target/test-data";

    /**
     * Produces a TestDataBuffer for PipeDoc objects.
     * This buffer is used to store output documents from processing.
     *
     * @return A TestDataBuffer for PipeDoc objects
     */
    @Produces
    @Singleton
    @Named("outputBuffer")
    public TestDataBuffer<PipeDoc> createOutputBuffer() {
        try {
            // !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
            // Force load the class using the current thread's context classloader
            // This ensures the class is loaded at the right time in Quarkus lifecycle
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> pipeDocClass = cl.loadClass("ai.pipestream.data.model.PipeDoc");

            Path outputPath = FileSystems.getDefault().getPath(OUTPUT_DIRECTORY);
            @SuppressWarnings("unchecked")
            TestDataBuffer<PipeDoc> buffer = new TestDataBuffer<>(DEFAULT_BUFFER_SIZE, (Class<PipeDoc>) pipeDocClass, outputPath, "output", 3);
            return buffer;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load PipeDoc class", e);
        }
    }

    /**
     * Produces a TestDataBuffer for PipeStream objects.
     * This buffer is used to store input streams for processing.
     *
     * @return A TestDataBuffer for PipeStream objects
     */
    @Produces
    @Singleton
    @Named("inputBuffer")
    public TestDataBuffer<PipeStream> createInputBuffer() {
        try {
            // !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
            // Force load the class using the current thread's context classloader
            // This ensures the class is loaded at the right time in Quarkus lifecycle
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> pipeStreamClass = cl.loadClass("ai.pipestream.data.model.PipeStream");

            Path inputPath = FileSystems.getDefault().getPath(OUTPUT_DIRECTORY);
            @SuppressWarnings("unchecked")
            TestDataBuffer<PipeStream> buffer = new TestDataBuffer<>(DEFAULT_BUFFER_SIZE, (Class<PipeStream>) pipeStreamClass, inputPath, "input", 3);
            return buffer;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load PipeStream class", e);
        }
    }
}
