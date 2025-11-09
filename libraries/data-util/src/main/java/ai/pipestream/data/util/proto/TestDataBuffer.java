package ai.pipestream.data.util.proto;

import com.google.protobuf.Message;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A buffer that stores protobuf messages and can save them to disk.
 * This is used for testing purposes to capture and save protobuf messages during test runs.
 * This is a copy of ProcessingBuffer from commons:util to avoid circular dependencies.
 *
 * @param <T> the type of protobuf message to store
 */
public class TestDataBuffer<T extends Message> {

    private static final Logger LOG = LoggerFactory.getLogger(TestDataBuffer.class);
    private static final String DEFAULT_PREFIX = "protobuf";
    private static final int DEFAULT_PRECISION = 3;
    private static final int MAX_PRECISION = 6;

    private final LinkedBlockingDeque<T> buffer;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int capacity;
    private final Class<T> messageClass;
    private final FileSystem fileSystem;
    private final Path defaultLocation;
    private final Path saveLocation;
    private final String savePrefix;
    private final int savePrecision;

    /**
     * Creates a new TestDataBuffer with the specified capacity, message class, file system, and save parameters.
     *
     * @param capacity the maximum number of messages to store
     * @param messageClass the class of the protobuf message
     * @param fileSystem the file system to use for saving files
     * @param saveLocation the directory to save the files to
     * @param savePrefix the prefix to use for the file names
     * @param savePrecision the number of digits to use for the file name suffix
     */
    public TestDataBuffer(int capacity, Class<T> messageClass, FileSystem fileSystem, 
                           Path saveLocation, String savePrefix, int savePrecision) {
        this.capacity = capacity;
        this.buffer = new LinkedBlockingDeque<>(capacity);
        this.messageClass = messageClass;
        this.fileSystem = fileSystem;
        this.defaultLocation = fileSystem.getPath("./");
        this.saveLocation = saveLocation != null ? saveLocation : this.defaultLocation;
        this.savePrefix = savePrefix != null ? savePrefix : DEFAULT_PREFIX;
        this.savePrecision = Math.max(1, Math.min(savePrecision, MAX_PRECISION));
    }

    /**
     * Creates a new TestDataBuffer with the specified capacity, message class, and save parameters.
     * Uses the default file system.
     *
     * @param capacity the maximum number of messages to store
     * @param messageClass the class of the protobuf message
     * @param saveLocation the directory to save the files to
     * @param savePrefix the prefix to use for the file names
     * @param savePrecision the number of digits to use for the file name suffix
     */
    public TestDataBuffer(int capacity, Class<T> messageClass, 
                           Path saveLocation, String savePrefix, int savePrecision) {
        this(capacity, messageClass, FileSystems.getDefault(), saveLocation, savePrefix, savePrecision);
    }

    /**
     * Creates a new TestDataBuffer with the specified capacity, message class, and file system.
     * Uses default save parameters.
     *
     * @param capacity the maximum number of messages to store
     * @param messageClass the class of the protobuf message
     * @param fileSystem the file system to use for saving files
     */
    public TestDataBuffer(int capacity, Class<T> messageClass, FileSystem fileSystem) {
        this(capacity, messageClass, fileSystem, fileSystem.getPath("./"), DEFAULT_PREFIX, DEFAULT_PRECISION);
    }

    /**
     * Creates a new TestDataBuffer with the specified capacity and message class.
     * Uses the default file system and default save parameters.
     *
     * @param capacity the maximum number of messages to store
     * @param messageClass the class of the protobuf message
     */
    public TestDataBuffer(int capacity, Class<T> messageClass) {
        this(capacity, messageClass, FileSystems.getDefault(), FileSystems.getDefault().getPath("./"), DEFAULT_PREFIX, DEFAULT_PRECISION);
    }

    /**
     * Saves the buffer contents to disk when the bean is destroyed.
     * This method is automatically called during application shutdown.
     * If the buffer is empty, nothing will be saved.
     */
    @PreDestroy
    public void saveOnShutdown() {
        if (buffer.isEmpty()) {
            LOG.debug("Buffer is empty, nothing to save on shutdown");
            return;
        }

        Path location = saveLocation != null ? saveLocation : defaultLocation;
        LOG.info("Saving {} messages from buffer to {} during shutdown", buffer.size(), location);
        saveToDisk(location, savePrefix, savePrecision);
    }

    /**
     * Adds a message to the buffer.
     * If the buffer is full, the oldest message will be removed.
     *
     * @param message the message to add
     */
    public void add(T message) {
        if (message == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            // Clone the message to prevent memory reference issues
            @SuppressWarnings("unchecked")
            T clonedMessage = (T) message.toBuilder().build();

            // If buffer is full, remove the oldest element
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
            }

            // Add the new message
            buffer.offerLast(clonedMessage);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Saves all messages in the buffer to disk using default parameters.
     * Default location is the current directory, default prefix is "protobuf",
     * and default precision is 3 digits.
     */
    public void saveToDisk() {
        saveToDisk(defaultLocation, DEFAULT_PREFIX, DEFAULT_PRECISION);
    }

    /**
     * Saves all messages in the buffer to disk to "<pre>./</pre>" directory.
     *
     * @param prefix the prefix to use for the file names
     * @param numberPrecision the number of digits to use for the file name suffix
     */
    public void saveToDisk(String prefix, int numberPrecision) {
        saveToDisk(defaultLocation, prefix, numberPrecision);
    }

    /**
     * Saves all messages in the buffer to disk.
     *
     * @param location the directory to save the files to
     * @param fileNamePrefix the prefix to use for the file names
     * @param numberPrecision the number of digits to use for the file name suffix
     */
    public void saveToDisk(Path location, String fileNamePrefix, int numberPrecision) {
        // Validate parameters
        if (location == null) {
            location = defaultLocation;
        }

        if (fileNamePrefix == null || fileNamePrefix.isEmpty()) {
            fileNamePrefix = DEFAULT_PREFIX;
        }

        // Ensure precision is between 1 and MAX_PRECISION
        numberPrecision = Math.max(1, Math.min(numberPrecision, MAX_PRECISION));

        lock.readLock().lock();
        try {
            // Create directory if it doesn't exist
            if (!Files.exists(location)) {
                try {
                    Files.createDirectories(location);
                } catch (IOException e) {
                    LOG.error("Failed to create directory: {}: {}", location, e.getMessage());
                    return;
                }
            }

            // Get a snapshot of the buffer
            List<T> messages = new ArrayList<>(buffer);

            // Save each message to disk
            for (int i = 0; i < messages.size(); i++) {
                T message = messages.get(i);
                // Use proper suffix format with dash separator
                String fileName = String.format("%s-%0" + numberPrecision + "d.bin", 
                        fileNamePrefix, i);
                Path filePath = location.resolve(fileName);

                try {
                    try (OutputStream os = Files.newOutputStream(filePath)) {
                        message.writeTo(os);
                    }
                    LOG.debug("Saved protobuf to {}", filePath);
                } catch (IOException e) {
                    LOG.error("Failed to save protobuf to {}: {}", filePath, e.getMessage());
                }
            }

            LOG.info("Saved {} protobufs to {}", messages.size(), location);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of messages in the buffer.
     *
     * @return the number of messages
     */
    public int size() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all messages from the buffer.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            buffer.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a snapshot of all items currently in the buffer.
     * This method creates a new list to prevent external modification of the buffer.
     * The returned list represents the buffer contents at the moment of the call.
     * This is useful for analysis and reporting.
     * 
     * @return a new list containing a snapshot of all messages in the buffer
     */
    public List<T> snapshot() {
        lock.readLock().lock();
        try {
            // Create a snapshot of the buffer contents at this moment
            return new ArrayList<>(buffer);
        } finally {
            lock.readLock().unlock();
        }
    }
}
