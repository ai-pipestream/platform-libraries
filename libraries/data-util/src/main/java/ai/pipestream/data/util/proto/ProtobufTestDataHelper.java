package ai.pipestream.data.util.proto;

import com.google.common.collect.Maps;

import com.google.protobuf.ExtensionRegistryLite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ai.pipestream.data.v1.PipeDoc;
import ai.pipestream.data.v1.PipeStream;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.quarkus.runtime.util.ClassPathUtils;

@ApplicationScoped
public class ProtobufTestDataHelper {

    /**
     * Default constructor for ProtobufTestDataHelper.
     * Creates a new instance with lazy-loaded collections and maps.
     */
    public ProtobufTestDataHelper() {
        // Default constructor
    }

    private static final Logger logger = LoggerFactory.getLogger(ProtobufTestDataHelper.class);

    // ExtensionRegistry for protobuf parsing
    private static final ExtensionRegistryLite EXTENSION_REGISTRY = ExtensionRegistryLite.newInstance();

    private static final String PIPE_DOC_DIRECTORY = "test-data/pipe-docs";
    private static final String PIPE_STREAM_DIRECTORY = "test-data/pipe-streams";
    private static final String TIKA_PIPE_DOC_DIRECTORY = "test-data/tika-pipe-docs";
    private static final String TIKA_PIPE_STREAM_DIRECTORY = "test-data/tika-pipe-streams";
    private static final String CHUNKER_PIPE_DOC_DIRECTORY = "test-data/chunker-pipe-docs";
    private static final String CHUNKER_PIPE_STREAM_DIRECTORY = "test-data/chunker-pipe-streams";
    private static final String SAMPLE_PIPE_DOC_DIRECTORY = "test-data/sample-pipe-docs";
    private static final String SAMPLE_PIPE_STREAM_DIRECTORY = "test-data/sample-pipe-streams";
    private static final String PIPELINE_GENERATED_DIRECTORY = "test-data/pipeline-generated";
    private static final String TIKA_REQUESTS_DIRECTORY = "test-data/tika/requests";
    private static final String TIKA_RESPONSES_DIRECTORY = "test-data/tika/responses";
    private final String CHUNKER_INPUT_DIRECTORY = "test-data/parser/output"; // Parser output becomes chunker input
    private final String CHUNKER_OUTPUT_DIRECTORY = "test-data/chunker-pipe-docs"; // Actual chunker output location
    private final String CHUNKER_OUTPUT_SMALL_DIRECTORY = "test-data/chunker/output/small";
    private final String EMBEDDER_INPUT_DIRECTORY = "test-data/embedder/input";
    private final String EMBEDDER_OUTPUT_DIRECTORY = "test-data/embedder/output";

    private final String FILE_EXTENSION = "bin";

    // Lazy-loaded collections and maps
    private volatile Collection<PipeDoc> pipeDocuments;
    private volatile Collection<PipeStream> pipeStreams;
    private volatile Collection<PipeDoc> tikaPipeDocuments;
    private volatile Collection<PipeStream> tikaPipeStreams;
    private volatile Collection<PipeDoc> chunkerPipeDocuments;
    private volatile Collection<PipeStream> chunkerPipeStreams;
    private volatile Collection<PipeDoc> samplePipeDocuments;
    private volatile Collection<PipeStream> samplePipeStreams;

    private volatile Map<String, PipeDoc> pipeDocumentsMap;
    private volatile Map<String, PipeStream> pipeStreamsMap;
    private volatile Map<String, PipeDoc> tikaPipeDocumentsMap;
    private volatile Map<String, PipeStream> tikaPipeStreamsMap;
    private volatile Map<String, PipeDoc> chunkerPipeDocumentsMap;
    private volatile Map<String, PipeStream> chunkerPipeStreamsMap;
    private volatile Map<String, PipeDoc> samplePipeDocumentsMap;
    private volatile Map<String, PipeStream> samplePipeStreamsMap;

    // Lazy-loaded ordered lists for sample documents
    private volatile List<PipeDoc> orderedSamplePipeDocs;
    private volatile List<PipeStream> orderedSamplePipeStreams;

    // Pipeline generated data
    private volatile Map<String, Collection<PipeDoc>> pipelineGeneratedDocs;

    // New generated test data - cached fields
    private volatile Collection<PipeStream> tikaRequestStreams;
    private volatile Collection<PipeDoc> tikaResponseDocuments;
    private volatile Collection<PipeDoc> tikaRequestDocuments;
    private volatile Collection<PipeDoc> chunkerInputDocuments;
    private volatile Collection<PipeStream> chunkerOutputStreams;
    private volatile Collection<PipeStream> chunkerOutputStreamsSmall;
    private volatile Collection<PipeDoc> embedderInputDocuments;
    private volatile Collection<PipeDoc> embedderOutputDocuments;
    private volatile Collection<PipeDoc> parserOutputDocs;

    /**
     * Creates a collection of ModuleProcessRequest objects for chunker input testing.
     *
     * @return A collection of ModuleProcessRequest objects.
     */
    public Collection<ai.pipestream.data.module.ModuleProcessRequest> getChunkerModuleProcessRequests() {
        return getChunkerInputDocuments().stream()
                .map(doc -> createModuleProcessRequest(doc, "chunker"))
                .collect(Collectors.toList());
    }

    /**
     * Creates a collection of ModuleProcessRequest objects for embedder input testing.
     *
     * @return A collection of ModuleProcessRequest objects.
     */
    public Collection<ai.pipestream.data.module.ModuleProcessRequest> getEmbedderModuleProcessRequests() {
        return getEmbedderInputDocuments().stream()
                .map(doc -> createModuleProcessRequest(doc, "embedder"))
                .collect(Collectors.toList());
    }

    /**
     * Creates a collection of ModuleProcessRequest objects from the sample documents.
     *
     * @return A collection of ModuleProcessRequest objects.
     */
    public Collection<ai.pipestream.data.module.ModuleProcessRequest> getSampleModuleProcessRequests() {
        return getSamplePipeDocuments().stream()
                .map(doc -> createModuleProcessRequest(doc, "sample-step"))
                .collect(Collectors.toList());
    }

    /**
     * Creates a ModuleProcessRequest for a given PipeDoc.
     *
     * @param pipeDoc The PipeDoc to include in the request.
     * @param pipeStepName The name of the pipe step for metadata.
     * @return A ModuleProcessRequest.
     */
    public ai.pipestream.data.module.ModuleProcessRequest createModuleProcessRequest(PipeDoc pipeDoc, String pipeStepName) {
        ai.pipestream.data.module.ServiceMetadata metadata = ai.pipestream.data.module.ServiceMetadata.newBuilder()
                .setPipelineName("test-pipeline")
                .setPipeStepName(pipeStepName)
                .setStreamId("stream-for-" + pipeDoc.getDocId())
                .build();

        return ai.pipestream.data.module.ModuleProcessRequest.newBuilder()
                .setDocument(pipeDoc)
                .setMetadata(metadata)
                .build();
    }

    /**
     * Retrieves a collection of PipeDoc objects.
     *
     * @return A collection of PipeDoc objects.
     */
    public Collection<PipeDoc> getPipeDocuments() {
        if (pipeDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (pipeDocuments == null) {
                    pipeDocuments = createPipeDocuments();
                }
            }
        }
        return pipeDocuments;
    }

    /**
     * Retrieves a collection of PipeStream objects.
     *
     * @return A collection of PipeStream objects.
     */
    public Collection<PipeStream> getPipeStreams() {
        if (pipeStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (pipeStreams == null) {
                    pipeStreams = createPipeStreams();
                }
            }
        }
        return pipeStreams;
    }

    /**
     * Retrieves a collection of PipeDoc objects from Tika parser.
     *
     * @return A collection of PipeDoc objects from Tika parser.
     */
    public Collection<PipeDoc> getTikaPipeDocuments() {
        if (tikaPipeDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaPipeDocuments == null) {
                    tikaPipeDocuments = createTikaPipeDocuments();
                }
            }
        }
        return tikaPipeDocuments;
    }

    /**
     * Retrieves a collection of PipeStream objects from Tika parser.
     *
     * @return A collection of PipeStream objects from Tika parser.
     */
    public Collection<PipeStream> getTikaPipeStreams() {
        if (tikaPipeStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaPipeStreams == null) {
                    tikaPipeStreams = createTikaPipeStreams();
                }
            }
        }
        return tikaPipeStreams;
    }

    /**
     * Retrieves a collection of PipeDoc objects from Chunker.
     *
     * @return A collection of PipeDoc objects from Chunker.
     */
    public Collection<PipeDoc> getChunkerPipeDocuments() {
        if (chunkerPipeDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerPipeDocuments == null) {
                    chunkerPipeDocuments = createChunkerPipeDocuments();
                }
            }
        }
        return chunkerPipeDocuments;
    }

    /**
     * Retrieves a collection of PipeStream objects from Chunker.
     *
     * @return A collection of PipeStream objects from Chunker.
     */
    public Collection<PipeStream> getChunkerPipeStreams() {
        if (chunkerPipeStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerPipeStreams == null) {
                    chunkerPipeStreams = createChunkerPipeStreams();
                }
            }
        }
        return chunkerPipeStreams;
    }

    /**
     * Retrieves a collection of PipeDoc objects from Sample Documents.
     *
     * @return A collection of PipeDoc objects from Sample Documents.
     */
    public Collection<PipeDoc> getSamplePipeDocuments() {
        if (samplePipeDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (samplePipeDocuments == null) {
                    samplePipeDocuments = createSamplePipeDocuments();
                }
            }
        }
        return samplePipeDocuments;
    }

    /**
     * Retrieves a collection of PipeStream objects from Sample Documents.
     *
     * @return A collection of PipeStream objects from Sample Documents.
     */
    public Collection<PipeStream> getSamplePipeStreams() {
        if (samplePipeStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (samplePipeStreams == null) {
                    samplePipeStreams = createSamplePipeStreams();
                }
            }
        }
        return samplePipeStreams;
    }

    /**
     * Retrieves a map of PipeDoc objects by ID.
     *
     * @return A map of PipeDoc objects by ID.
     */
    public Map<String, PipeDoc> getPipeDocumentsMap() {
        if (pipeDocumentsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (pipeDocumentsMap == null) {
                    pipeDocumentsMap = createPipeDocumentMapById();
                }
            }
        }
        return pipeDocumentsMap;
    }

    /**
     * Retrieves a map of PipeStream objects by stream ID.
     *
     * @return A map of PipeStream objects by stream ID.
     */
    public Map<String, PipeStream> getPipeStreamsMap() {
        if (pipeStreamsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (pipeStreamsMap == null) {
                    pipeStreamsMap = createPipeStreamMapById();
                }
            }
        }
        return pipeStreamsMap;
    }

    /**
     * Retrieves a map of Tika PipeDoc objects by ID.
     *
     * @return A map of Tika PipeDoc objects by ID.
     */
    public Map<String, PipeDoc> getTikaPipeDocumentsMap() {
        if (tikaPipeDocumentsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaPipeDocumentsMap == null) {
                    tikaPipeDocumentsMap = createTikaPipeDocumentMapById();
                }
            }
        }
        return tikaPipeDocumentsMap;
    }

    /**
     * Retrieves a map of Tika PipeStream objects by stream ID.
     *
     * @return A map of Tika PipeStream objects by stream ID.
     */
    public Map<String, PipeStream> getTikaPipeStreamsMap() {
        if (tikaPipeStreamsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaPipeStreamsMap == null) {
                    tikaPipeStreamsMap = createTikaPipeStreamMapById();
                }
            }
        }
        return tikaPipeStreamsMap;
    }

    /**
     * Retrieves a map of Chunker PipeDoc objects by ID.
     *
     * @return A map of Chunker PipeDoc objects by ID.
     */
    public Map<String, PipeDoc> getChunkerPipeDocumentsMap() {
        if (chunkerPipeDocumentsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerPipeDocumentsMap == null) {
                    chunkerPipeDocumentsMap = createChunkerPipeDocumentMapById();
                }
            }
        }
        return chunkerPipeDocumentsMap;
    }

    /**
     * Retrieves a map of Chunker PipeStream objects by stream ID.
     *
     * @return A map of Chunker PipeStream objects by stream ID.
     */
    public Map<String, PipeStream> getChunkerPipeStreamsMap() {
        if (chunkerPipeStreamsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerPipeStreamsMap == null) {
                    chunkerPipeStreamsMap = createChunkerPipeStreamMapById();
                }
            }
        }
        return chunkerPipeStreamsMap;
    }

    /**
     * Retrieves a map of Sample PipeDoc objects by ID.
     *
     * @return A map of Sample PipeDoc objects by ID.
     */
    public Map<String, PipeDoc> getSamplePipeDocumentsMap() {
        if (samplePipeDocumentsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (samplePipeDocumentsMap == null) {
                    samplePipeDocumentsMap = createSamplePipeDocumentMapById();
                }
            }
        }
        return samplePipeDocumentsMap;
    }

    /**
     * Retrieves a map of Sample PipeStream objects by stream ID.
     *
     * @return A map of Sample PipeStream objects by stream ID.
     */
    public Map<String, PipeStream> getSamplePipeStreamsMap() {
        if (samplePipeStreamsMap == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (samplePipeStreamsMap == null) {
                    samplePipeStreamsMap = createSamplePipeStreamMapById();
                }
            }
        }
        return samplePipeStreamsMap;
    }

    /**
     * Retrieves an ordered list of PipeDoc objects from the Sample Documents.
     *
     * @return An ordered list of PipeDoc objects from the Sample Documents.
     */
    public List<PipeDoc> getOrderedSamplePipeDocuments() {
        if (orderedSamplePipeDocs == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (orderedSamplePipeDocs == null) {
                    Collection<PipeDoc> docs = getSamplePipeDocuments();
                    orderedSamplePipeDocs = docs.stream()
                            .sorted(Comparator.comparing(doc -> {
                                String id = doc.getDocId();
                                // Extract the index from the ID (assuming format "doc-XXXXXXXX")
                                return id;
                            }))
                            .collect(Collectors.toList());
                }
            }
        }
        return orderedSamplePipeDocs;
    }

    /**
     * Retrieves an ordered list of PipeStream objects from the Sample Documents.
     *
     * @return An ordered list of PipeStream objects from the Sample Documents.
     */
    public List<PipeStream> getOrderedSamplePipeStreams() {
        if (orderedSamplePipeStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (orderedSamplePipeStreams == null) {
                    Collection<PipeStream> streams = getSamplePipeStreams();
                    orderedSamplePipeStreams = streams.stream()
                            .sorted(Comparator.comparing(stream -> {
                                String id = stream.getStreamId();
                                // Extract the index from the ID (assuming format "stream-XXXXXXXX")
                                return id;
                            }))
                            .collect(Collectors.toList());
                }
            }
        }
        return orderedSamplePipeStreams;
    }

    /**
     * Retrieves a specific PipeDoc object from the Sample Documents by index.
     *
     * @param index The index of the PipeDoc object to retrieve.
     * @return The PipeDoc object at the specified index, or null if not found.
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public PipeDoc getSamplePipeDocByIndex(int index) {
        List<PipeDoc> orderedDocs = getOrderedSamplePipeDocuments();
        if (index < 0 || index >= orderedDocs.size()) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (orderedDocs.size() - 1) + ", inclusive. Actual size: " + orderedDocs.size());
        }
        return orderedDocs.get(index);
    }

    /**
     * Retrieves pipeline generated documents for a specific stage.
     * Stages include: "after-chunker1", "after-chunker2", "after-embedder1", "after-embedder2"
     *
     * @param stage The pipeline stage to retrieve documents from
     * @return A collection of PipeDoc objects from that stage
     */
    public Collection<PipeDoc> getPipelineGeneratedDocuments(String stage) {
        if (pipelineGeneratedDocs == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (pipelineGeneratedDocs == null) {
                    pipelineGeneratedDocs = loadPipelineGeneratedDocuments();
                }
            }
        }
        return pipelineGeneratedDocs.getOrDefault(stage, Collections.emptyList());
    }

    /**
     * Retrieves Tika request streams (input data with document blobs).
     *
     * @return A collection of PipeStream objects for Tika input testing
     */
    public Collection<PipeStream> getTikaRequestStreams() {
        if (tikaRequestStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaRequestStreams == null) {
                    try {
                        tikaRequestStreams = loadPipeStreamsFromDirectory(TIKA_REQUESTS_DIRECTORY);
                    } catch (Exception e) {
                        tikaRequestStreams = Collections.emptyList();
                    }
                }
            }
        }
        return tikaRequestStreams;
    }

    /**
     * Retrieves Tika response documents (output data with extracted text).
     *
     * @return A collection of PipeDoc objects for Tika output testing
     */
    public Collection<PipeDoc> getTikaResponseDocuments() {
        if (tikaResponseDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaResponseDocuments == null) {
                    try {
                        tikaResponseDocuments = loadPipeDocsFromDirectory(TIKA_RESPONSES_DIRECTORY);
                    } catch (Exception e) {
                        tikaResponseDocuments = Collections.emptyList();
                    }
                }
            }
        }
        return tikaResponseDocuments;
    }

    /**
     * Retrieves Tika request documents (input PipeDocs with blobs extracted from PipeStreams).
     * This is a convenience method that extracts PipeDoc objects from the Tika request PipeStreams.
     *
     * @return A collection of PipeDoc objects for Tika input testing
     */
    public Collection<PipeDoc> getTikaRequestDocuments() {
        if (tikaRequestDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (tikaRequestDocuments == null) {
                    tikaRequestDocuments = getTikaRequestStreams().stream()
                        .map(PipeStream::getDocument)
                        .collect(Collectors.toList());
                }
            }
        }
        return tikaRequestDocuments;
    }

    /**
     * Retrieves chunker input documents (Tika-processed documents ready for chunking).
     *
     * @return A collection of PipeDoc objects for chunker input testing
     */
    public Collection<PipeDoc> getChunkerInputDocuments() {
        if (chunkerInputDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerInputDocuments == null) {
                    try {
                        chunkerInputDocuments = loadPipeDocsFromDirectory(CHUNKER_INPUT_DIRECTORY);
                    } catch (Exception e) {
                        chunkerInputDocuments = Collections.emptyList();
                    }
                }
            }
        }
        return chunkerInputDocuments;
    }

    /**
     * Retrieves chunker output streams (documents with semantic chunks).
     *
     * @return A collection of PipeStream objects for chunker output testing
     */
    public Collection<PipeStream> getChunkerOutputStreams() {
        if (chunkerOutputStreams == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerOutputStreams == null) {
                    try {
                        chunkerOutputStreams = loadPipeStreamsFromDirectory(CHUNKER_OUTPUT_DIRECTORY);
                    } catch (Exception e) {
                        chunkerOutputStreams = Collections.emptyList();
                    }
                }
            }
        }
        return chunkerOutputStreams;
    }

    /**
     * Retrieves chunker output streams with small chunks (documents with smaller semantic chunks).
     * These are generated with a different chunker configuration (smaller chunk sizes).
     *
     * @return A collection of PipeStream objects for chunker output testing with small chunks
     */
    public Collection<PipeStream> getChunkerOutputStreamsSmall() {
        if (chunkerOutputStreamsSmall == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (chunkerOutputStreamsSmall == null) {
                    try {
                        chunkerOutputStreamsSmall = loadPipeStreamsFromDirectory(CHUNKER_OUTPUT_SMALL_DIRECTORY);
                    } catch (Exception e) {
                        chunkerOutputStreamsSmall = Collections.emptyList();
                    }
                }
            }
        }
        return chunkerOutputStreamsSmall;
    }

    /**
     * Retrieves all chunker output streams (both default and small chunks).
     * This combines the results of getChunkerOutputStreams() and getChunkerOutputStreamsSmall().
     *
     * @return A collection of PipeStream objects for all chunker output testing
     */
    public Collection<PipeStream> getAllChunkerOutputStreams() {
        Collection<PipeStream> result = new ArrayList<>();
        result.addAll(getChunkerOutputStreams());
        result.addAll(getChunkerOutputStreamsSmall());
        return result;
    }

    /**
     * Retrieves embedder input documents (chunked documents ready for embedding).
     *
     * @return A collection of PipeDoc objects for embedder input testing
     */
    public Collection<PipeDoc> getEmbedderInputDocuments() {
        if (embedderInputDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (embedderInputDocuments == null) {
                    try {
                        embedderInputDocuments = loadPipeDocsFromDirectory(EMBEDDER_INPUT_DIRECTORY);
                    }
                    catch (Exception e) {
                        embedderInputDocuments = Collections.emptyList();
                    }
                }
            }
        }
        return embedderInputDocuments;
    }

    /**
     * Retrieves embedder output documents (documents with vector embeddings).
     *
     * @return A collection of PipeDoc objects for embedder output testing
     */
    public Collection<PipeDoc> getEmbedderOutputDocuments() {
        if (embedderOutputDocuments == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (embedderOutputDocuments == null) {
                    try {
                        embedderOutputDocuments = loadPipeDocsFromDirectory(EMBEDDER_OUTPUT_DIRECTORY);
                    }
                    catch (Exception e) {
                        embedderOutputDocuments = Collections.emptyList();
                    }
                }
            }
        }
        return embedderOutputDocuments;
    }

    /**
     * Get parser output documents from test-data/parser/output
     * These are parsed documents that can be used as input for chunking tests
     * @return Collection of parsed PipeDoc instances
     */
    public Collection<PipeDoc> getParserOutputDocs() {
        if (parserOutputDocs == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (parserOutputDocs == null) {
                    parserOutputDocs = loadPipeDocsFromDirectory(CHUNKER_INPUT_DIRECTORY);
                }
            }
        }
        return parserOutputDocs;
    }

    /**
     * Retrieves all pipeline stages available.
     *
     * @return A set of stage names
     */
    public Set<String> getPipelineStages() {
        if (pipelineGeneratedDocs == null) {
            synchronized (ProtobufTestDataHelper.class) {
                if (pipelineGeneratedDocs == null) {
                    pipelineGeneratedDocs = loadPipelineGeneratedDocuments();
                }
            }
        }
        return pipelineGeneratedDocs.keySet();
    }

    /**
     * Retrieves a specific PipeStream object from the Sample Documents by index.
     *
     * @param index The index of the PipeStream object to retrieve.
     * @return The PipeStream object at the specified index, or null if not found.
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public PipeStream getSamplePipeStreamByIndex(int index) {
        List<PipeStream> orderedStreams = getOrderedSamplePipeStreams();
        if (index < 0 || index >= orderedStreams.size()) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (orderedStreams.size() - 1) + ", inclusive. Actual size: " + orderedStreams.size());
        }
        return orderedStreams.get(index);
    }

    /**
     * Creates a map of PipeDoc objects by ID.
     *
     * @return A map of PipeDoc objects by ID.
     */
    private Map<String, PipeDoc> createPipeDocumentMapById() {
        Collection<PipeDoc> docs = getPipeDocuments();
        Map<String, PipeDoc> returnVal = Maps.newHashMapWithExpectedSize(docs.size());
        docs.forEach((doc) -> returnVal.put(doc.getDocId(), doc));
        return returnVal;
    }

    /**
     * Creates a map of PipeStream objects by stream ID.
     *
     * @return A map of PipeStream objects by stream ID.
     */
    private Map<String, PipeStream> createPipeStreamMapById() {
        Collection<PipeStream> streams = getPipeStreams();
        Map<String, PipeStream> returnVal = Maps.newHashMapWithExpectedSize(streams.size());
        streams.forEach((stream) -> returnVal.put(stream.getStreamId(), stream));
        return returnVal;
    }

    /**
     * Creates a map of Tika PipeDoc objects by ID.
     *
     * @return A map of Tika PipeDoc objects by ID.
     */
    private Map<String, PipeDoc> createTikaPipeDocumentMapById() {
        Collection<PipeDoc> docs = getTikaPipeDocuments();
        Map<String, PipeDoc> returnVal = Maps.newHashMapWithExpectedSize(docs.size());
        docs.forEach((doc) -> returnVal.put(doc.getDocId(), doc));
        return returnVal;
    }

    /**
     * Creates a map of Tika PipeStream objects by stream ID.
     *
     * @return A map of Tika PipeStream objects by stream ID.
     */
    private Map<String, PipeStream> createTikaPipeStreamMapById() {
        Collection<PipeStream> streams = getTikaPipeStreams();
        Map<String, PipeStream> returnVal = Maps.newHashMapWithExpectedSize(streams.size());
        streams.forEach((stream) -> returnVal.put(stream.getStreamId(), stream));
        return returnVal;
    }

    /**
     * Creates a map of Chunker PipeDoc objects by ID.
     *
     * @return A map of Chunker PipeDoc objects by ID.
     */
    private Map<String, PipeDoc> createChunkerPipeDocumentMapById() {
        Collection<PipeDoc> docs = getChunkerPipeDocuments();
        Map<String, PipeDoc> returnVal = Maps.newHashMapWithExpectedSize(docs.size());
        docs.forEach((doc) -> returnVal.put(doc.getDocId(), doc));
        return returnVal;
    }

    /**
     * Creates a map of Chunker PipeStream objects by stream ID.
     *
     * @return A map of Chunker PipeStream objects by stream ID.
     */
    private Map<String, PipeStream> createChunkerPipeStreamMapById() {
        Collection<PipeStream> streams = getChunkerPipeStreams();
        Map<String, PipeStream> returnVal = Maps.newHashMapWithExpectedSize(streams.size());
        streams.forEach((stream) -> returnVal.put(stream.getStreamId(), stream));
        return returnVal;
    }

    /**
     * Creates a map of Sample PipeDoc objects by ID.
     *
     * @return A map of Sample PipeDoc objects by ID.
     */
    private Map<String, PipeDoc> createSamplePipeDocumentMapById() {
        Collection<PipeDoc> docs = getSamplePipeDocuments();
        Map<String, PipeDoc> returnVal = Maps.newHashMapWithExpectedSize(docs.size());
        docs.forEach((doc) -> returnVal.put(doc.getDocId(), doc));
        return returnVal;
    }

    /**
     * Creates a map of Sample PipeStream objects by stream ID.
     *
     * @return A map of Sample PipeStream objects by stream ID.
     */
    private Map<String, PipeStream> createSamplePipeStreamMapById() {
        Collection<PipeStream> streams = getSamplePipeStreams();
        Map<String, PipeStream> returnVal = Maps.newHashMapWithExpectedSize(streams.size());
        streams.forEach((stream) -> returnVal.put(stream.getStreamId(), stream));
        return returnVal;
    }

    /**
     * Creates a collection of PipeDoc objects from the specified directory.
     *
     * @return A collection of PipeDoc objects from the specified directory.
     */
    private Collection<PipeDoc> createPipeDocuments() {
        try {
            return loadPipeDocsFromDirectory(PIPE_DOC_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeStream objects from the specified directory.
     *
     * @return A collection of PipeStream objects from the specified directory.
     */
    private Collection<PipeStream> createPipeStreams() {
        try {
            return loadPipeStreamsFromDirectory(PIPE_STREAM_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeDoc objects from the Tika parser directory.
     *
     * @return A collection of PipeDoc objects from the Tika parser directory.
     */
    private Collection<PipeDoc> createTikaPipeDocuments() {
        try {
            return loadPipeDocsFromDirectory(TIKA_PIPE_DOC_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeStream objects from the Tika parser directory.
     *
     * @return A collection of PipeStream objects from the Tika parser directory.
     */
    private Collection<PipeStream> createTikaPipeStreams() {
        try {
            return loadPipeStreamsFromDirectory(TIKA_PIPE_STREAM_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeDoc objects from the Chunker directory.
     *
     * @return A collection of PipeDoc objects from the Chunker directory.
     */
    private Collection<PipeDoc> createChunkerPipeDocuments() {
        try {
            return loadPipeDocsFromDirectory(CHUNKER_PIPE_DOC_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeStream objects from the Chunker directory.
     *
     * @return A collection of PipeStream objects from the Chunker directory.
     */
    private Collection<PipeStream> createChunkerPipeStreams() {
        try {
            return loadPipeStreamsFromDirectory(CHUNKER_PIPE_STREAM_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeDoc objects from the Sample Documents directory.
     *
     * @return A collection of PipeDoc objects from the Sample Documents.
     */
    private Collection<PipeDoc> createSamplePipeDocuments() {
        try {
            return loadPipeDocsFromDirectory(SAMPLE_PIPE_DOC_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Creates a collection of PipeStream objects from the Sample Documents directory.
     *
     * @return A collection of PipeStream objects from the Sample Documents.
     */
    private Collection<PipeStream> createSamplePipeStreams() {
        try {
            return loadPipeStreamsFromDirectory(SAMPLE_PIPE_STREAM_DIRECTORY);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Loads PipeDoc objects from the specified directory.
     *
     * @param directory The directory to load PipeDoc objects from.
     * @return A collection of PipeDoc objects.
     * @throws IOException If an I/O error occurs.
     */
    private Collection<PipeDoc> loadPipeDocsFromDirectory(String directory) {
    try {
        // !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> pipeDocClass = cl.loadClass("ai.pipestream.data.v1.PipeDoc");
        var parserMethod = pipeDocClass.getMethod("parser");
        @SuppressWarnings("unchecked")
        var parser = (com.google.protobuf.Parser<PipeDoc>) parserMethod.invoke(null);

        return loadFromClasspathDirectory(directory, "bin", parser);
    } catch (Exception e) {
        logger.error("Failed to load PipeDoc class or parser", e);
        return Collections.emptyList();
    }
}

private Collection<PipeStream> loadPipeStreamsFromDirectory(String directory) {
    try {
        // !!!! CRITICAL - DO NOT DELETE THIS FORCE LOADING !!!!
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> pipeStreamClass = cl.loadClass("ai.pipestream.data.v1.PipeStream");
        var parserMethod = pipeStreamClass.getMethod("parser");
        @SuppressWarnings("unchecked")
        var parser = (com.google.protobuf.Parser<PipeStream>) parserMethod.invoke(null);

        return loadFromClasspathDirectory(directory, "bin", parser);
    } catch (Exception e) {
        logger.error("Failed to load PipeStream class or parser", e);
        return Collections.emptyList();
    }
}

    

    /**
     * Load protobuf files from classpath directory by using ClassPathUtils to list resources.
     * This works by directly listing the resources in the directory, which works for both
     * filesystem resources and JAR resources.
     */
    private <T extends com.google.protobuf.MessageLite> Collection<T> loadFromClasspathDirectory(
            String directory, String fileExtension, com.google.protobuf.Parser<T> parser) {

        List<T> messages = new ArrayList<>();

        try {
            ClassPathUtils.consumeAsPaths(directory, path -> {
                try (Stream<Path> files = Files.walk(path)) {
                    files.filter(p -> p.toString().endsWith("." + fileExtension))
                         .forEach(filePath -> {
                             try (InputStream is = Files.newInputStream(filePath)) {
                                 T message = parser.parseFrom(is, EXTENSION_REGISTRY);
                                 if (message != null) {
                                     messages.add(message);
                                 }
                             } catch (IOException e) {
                                 logger.error("Failed to read file: {}", filePath, e.getCause());
                             }
                         });
                } catch (IOException e) {
                    logger.error("Failed to walk path: " + path, e);
                }
            });
        } catch (IOException e) {
            logger.error("Failed to consume paths for directory: " + directory, e);
        }

        return messages;
    }

    

    

    

    /**
     * Loads pipeline generated documents from all stages.
     *
     * @return A map of stage name to collection of PipeDoc objects
     */
    private  Map<String, Collection<PipeDoc>> loadPipelineGeneratedDocuments() {
        Map<String, Collection<PipeDoc>> result = new HashMap<>();

        try {
            ClassPathUtils.consumeAsPaths(PIPELINE_GENERATED_DIRECTORY, pipelineDir -> {
                try (Stream<Path> stages = Files.list(pipelineDir)) {
                    stages.filter(Files::isDirectory).forEach(stageDir -> {
                        String stageName = stageDir.getFileName().toString();
                        try {
                            String relativePath = "/" + ClassPathUtils.toResourceName(pipelineDir.relativize(stageDir));
                            Collection<PipeDoc> docs = loadPipeDocsFromDirectory(relativePath);
                            result.put(stageName, docs);
                        } catch (Exception e) {
                            // Skip this stage on error
                            result.put(stageName, Collections.emptyList());
                        }
                    });
                } catch (Exception e) {
                    // Skip on error
                }
            });
        } catch (Exception e) {
            // Return empty map on any error
            return Collections.emptyMap();
        }

        return result;
    }
}