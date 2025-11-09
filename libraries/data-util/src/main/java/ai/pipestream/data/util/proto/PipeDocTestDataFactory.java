package ai.pipestream.data.util.proto;

import com.google.protobuf.Any;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import ai.pipestream.data.v1.*;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Factory for creating numbered test PipeDoc instances using protobuf builders directly.
 * Generates predictable test data with document numbers for easy testing.
 */
@Singleton
public class PipeDocTestDataFactory {

    private final List<String> DOCUMENT_TYPES = Arrays.asList("article", "report", "email", "manual", "blog");
    private final List<String> CATEGORIES = Arrays.asList("technology", "business", "science", "education", "news");
    private final List<String> LANGUAGES = Arrays.asList("en", "es", "fr", "de", "it");

    /**
     * Creates a minimal PipeDoc with just doc_id
     *
     * @param number sequential identifier used to generate deterministic values
     * @return PipeDoc containing only doc_id populated
     */
    public PipeDoc createMinimalDocument(int number) {
        return PipeDoc.newBuilder()
                .setDocId("test-doc-" + String.format("%03d", number))
                .build();
    }

    /**
     * Creates a basic PipeDoc with search metadata
     *
     * @param number sequential identifier used to generate deterministic values
     * @return PipeDoc with doc_id and basic search metadata populated
     */
    public PipeDoc createBasicDocument(int number) {
        return PipeDoc.newBuilder()
                .setDocId("test-doc-" + String.format("%03d", number))
                .setSearchMetadata(createBasicSearchMetadata(number))
                .build();
    }

    /**
     * Creates a complex PipeDoc with all components
     *
     * @param number sequential identifier used to generate deterministic values
     * @return PipeDoc with search metadata, blob bag, and structured data populated
     */
    public PipeDoc createComplexDocument(int number) {
        return PipeDoc.newBuilder()
                .setDocId("test-doc-" + String.format("%03d", number))
                .setSearchMetadata(createComplexSearchMetadata(number))
                .setBlobBag(createBlobBag(number))
                .setStructuredData(createStructuredData(number))
                .build();
    }

    /**
     * Creates a document with single blob
     *
     * @param number sequential identifier used to generate deterministic values
     * @return PipeDoc with one Blob inside a BlobBag
     */
    public PipeDoc createDocumentWithBlob(int number) {
        return PipeDoc.newBuilder()
                .setDocId("test-doc-" + String.format("%03d", number))
                .setSearchMetadata(createBasicSearchMetadata(number))
                .setBlobBag(createSingleBlobBag(number))
                .build();
    }

    /**
     * Creates a document with multiple blobs
     *
     * @param number sequential identifier used to generate deterministic values
     * @return PipeDoc with a BlobBag containing multiple Blob entries
     */
    public PipeDoc createDocumentWithMultipleBlobs(int number) {
        return PipeDoc.newBuilder()
                .setDocId("test-doc-" + String.format("%03d", number))
                .setSearchMetadata(createBasicSearchMetadata(number))
                .setBlobBag(createMultipleBlobsBag(number))
                .build();
    }

    // Helper methods for building components

    private SearchMetadata createBasicSearchMetadata(int number) {
        return SearchMetadata.newBuilder()
                .setTitle("Test Document " + number)
                .setBody("This is test content for document number " + number + ". It contains sample text for testing purposes.")
                .setDocumentType(DOCUMENT_TYPES.get(number % DOCUMENT_TYPES.size()))
                .setSourceUri("test://source/" + number)
                .setCreationDate(createTimestamp())
                .build();
    }

    private SearchMetadata createComplexSearchMetadata(int number) {
        return SearchMetadata.newBuilder()
                .setTitle("Complex Test Document " + number)
                .setBody("This is comprehensive test content for document number " + number + ". " +
                        "It includes multiple paragraphs and detailed information for testing complex scenarios. " +
                        "The content is designed to test various aspects of document processing and storage.")
                .setDocumentType(DOCUMENT_TYPES.get(number % DOCUMENT_TYPES.size()))
                .setSourceUri("test://complex-source/" + number)
                .setSourceMimeType("text/plain")
                .setCreationDate(createTimestamp())
                .setLastModifiedDate(createTimestamp())
                .setProcessedDate(createTimestamp())
                .setLanguage(LANGUAGES.get(number % LANGUAGES.size()))
                .setAuthor("Test Author " + number)
                .setCategory(CATEGORIES.get(number % CATEGORIES.size()))
                .setTags(createTags(number))
                .setContentLength(500 + (number * 10))
                .setRelevanceScore(0.5 + (number % 5) * 0.1)
                .setCustomFields(createCustomFields(number))
                .setKeywords(createKeywords(number))
                .addSemanticResults(createSemanticResult(number))
                .build();
    }

    private Tags createTags(int number) {
        return Tags.newBuilder()
                .putTagData("environment", "test")
                .putTagData("category", CATEGORIES.get(number % CATEGORIES.size()))
                .putTagData("priority", number % 2 == 0 ? "high" : "normal")
                .putTagData("version", "1." + (number % 10))
                .putTagData("document_number", String.valueOf(number))
                .build();
    }

    private Keywords createKeywords(int number) {
        return Keywords.newBuilder()
                .addKeyword("test")
                .addKeyword("document")
                .addKeyword("number-" + number)
                .addKeyword(CATEGORIES.get(number % CATEGORIES.size()))
                .build();
    }

    private Struct createCustomFields(int number) {
        return Struct.newBuilder()
                .putFields("test_number", Value.newBuilder().setNumberValue(number).build())
                .putFields("test_name", Value.newBuilder().setStringValue("test-" + number).build())
                .putFields("is_even", Value.newBuilder().setBoolValue(number % 2 == 0).build())
                .build();
    }

    private SemanticProcessingResult createSemanticResult(int number) {
        return SemanticProcessingResult.newBuilder()
                .setResultId("semantic-result-" + number)
                .setSourceFieldName("body")
                .setChunkConfigId("test-chunker-v1")
                .setEmbeddingConfigId("test-embedder-v1")
                .setResultSetName("body_chunks_test_" + number)
                .addChunks(createSemanticChunk(number, 1))
                .addChunks(createSemanticChunk(number, 2))
                .putMetadata("processing_time", Value.newBuilder().setNumberValue(100 + number).build())
                .build();
    }

    private SemanticChunk createSemanticChunk(int docNumber, int chunkNumber) {
        return SemanticChunk.newBuilder()
                .setChunkId("chunk-" + docNumber + "-" + chunkNumber)
                .setChunkNumber(chunkNumber)
                .setEmbeddingInfo(ChunkEmbedding.newBuilder()
                        .setTextContent("Chunk " + chunkNumber + " content for document " + docNumber)
                        .addVector(0.1f * docNumber)
                        .addVector(0.2f * chunkNumber)
                        .addVector(0.3f * (docNumber + chunkNumber))
                        .setChunkId("chunk-" + docNumber + "-" + chunkNumber)
                        .setOriginalCharStartOffset(chunkNumber * 100)
                        .setOriginalCharEndOffset((chunkNumber + 1) * 100)
                        .build())
                .putMetadata("chunk_type", Value.newBuilder().setStringValue("test").build())
                .build();
    }

    private BlobBag createBlobBag(int number) {
        return number % 2 == 0 ? createSingleBlobBag(number) : createMultipleBlobsBag(number);
    }

    private BlobBag createSingleBlobBag(int number) {
        return BlobBag.newBuilder()
                .setBlob(createBlob(number, "single"))
                .build();
    }

    private BlobBag createMultipleBlobsBag(int number) {
        return BlobBag.newBuilder()
                .setBlobs(Blobs.newBuilder()
                        .addBlob(createBlob(number, "first"))
                        .addBlob(createBlob(number, "second"))
                        .build())
                .build();
    }

    private Blob createBlob(int number, String suffix) {
        String content = "Binary content for document " + number + " (" + suffix + ")";
        return Blob.newBuilder()
                .setBlobId("blob-" + number + "-" + suffix)
                .setData(com.google.protobuf.ByteString.copyFromUtf8(content))
                .setMimeType("text/plain")
                .setFilename("test-file-" + number + "-" + suffix + ".txt")
                .setEncoding("UTF-8")
                .setSizeBytes(content.length())
                .setChecksum("checksum-" + number + "-" + suffix)
                .setChecksumType(ChecksumType.CHECKSUM_TYPE_SHA256)
                .setMetadata(Struct.newBuilder()
                        .putFields("blob_number", Value.newBuilder().setNumberValue(number).build())
                        .putFields("blob_suffix", Value.newBuilder().setStringValue(suffix).build())
                        .build())
                .build();
    }

    private Any createStructuredData(int number) {
        // Create a simple Struct as structured data
        Struct customData = Struct.newBuilder()
                .putFields("document_id", Value.newBuilder().setNumberValue(number).build())
                .putFields("custom_field", Value.newBuilder().setStringValue("custom-value-" + number).build())
                .putFields("metadata", Value.newBuilder()
                        .setStructValue(Struct.newBuilder()
                                .putFields("nested_field", Value.newBuilder().setStringValue("nested-" + number).build())
                                .build())
                        .build())
                .build();
        
        return Any.pack(customData);
    }

    private Timestamp createTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }

    // Utility methods for creating test request data

    // TODO: Disabled - CreatePipeDocRequest interface removed during refactor
    // /**
    //  * Creates a CreatePipeDocRequest for testing
    //  */
    // public ai.pipestream.repository.v1.CreatePipeDocRequest createPipeDocRequest(int number) {
    //     return ai.pipestream.repository.v1.CreatePipeDocRequest.newBuilder()
    //             .setPipeDoc(createBasicDocument(number))
    //             .setTags(createTags(number))
    //             .setDescription("Test document " + number + " for repository testing")
    //             .build();
    // }

    // /**
    //  * Creates a complex CreatePipeDocRequest for testing
    //  */
    // public ai.pipestream.repository.v1.CreatePipeDocRequest createComplexPipeDocRequest(int number) {
    //     return ai.pipestream.repository.v1.CreatePipeDocRequest.newBuilder()
    //             .setPipeDoc(createComplexDocument(number))
    //             .setTags(createTags(number))
    //             .setDescription("Complex test document " + number + " with all features for comprehensive testing")
    //             .build();
    // }

    // Collection methods for bulk testing

    /**
     * Gets a sample PipeDoc by index
     *
     * @param index sequential identifier used to generate deterministic values
     * @return a complex PipeDoc generated from the given index
     */
    public PipeDoc getSamplePipeDocByIndex(int index) {
        return createComplexDocument(index);
    }

    /**
     * Creates PipeStreams that would come from Tika processing
     *
     * @return a list of sample PipeStream instances representing Tika outputs
     */
    public Collection<PipeStream> getTikaPipeStreams() {
        return IntStream.range(1, 6)
                .mapToObj(this::createTikaPipeStream)
                .collect(Collectors.toList());
    }

    /**
     * Creates PipeDocuments that would be input to chunker
     *
     * @return a list of sample PipeDoc instances representing pre-chunker inputs
     */
    public Collection<PipeDoc> getChunkerPipeDocuments() {
        return IntStream.range(1, 6)
                .mapToObj(this::createChunkerInputDocument)
                .collect(Collectors.toList());
    }

    /**
     * Creates PipeDocuments that would be input to embedder
     *
     * @return a list of sample PipeDoc instances representing embedder inputs
     */
    public Collection<PipeDoc> getEmbedderInputDocuments() {
        return IntStream.range(1, 6)
                .mapToObj(this::createEmbedderInputDocument)
                .collect(Collectors.toList());
    }

    /**
     * Creates PipeDocuments that would be output from embedder
     */
    public Collection<PipeDoc> getEmbedderOutputDocuments() {
        return IntStream.range(1, 6)
                .mapToObj(this::createEmbedderOutputDocument)
                .collect(Collectors.toList());
    }

    // Pipeline-specific document creators

    private PipeStream createTikaPipeStream(int number) {
        PipeDoc tikaDoc = PipeDoc.newBuilder()
                .setDocId("tika-doc-" + String.format("%03d", number))
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setTitle("Tika Processed Document " + number)
                        .setBody("Content extracted by Tika from document " + number + ". This includes parsed text, metadata, and structural information.")
                        .setDocumentType("pdf")
                        .setSourceUri("file://input/document-" + number + ".pdf")
                        .setSourceMimeType("application/pdf")
                        .setCreationDate(createTimestamp())
                        .setProcessedDate(createTimestamp())
                        .setAuthor("Original Author " + number)
                        .setContentLength(1000 + (number * 50))
                        .build())
                .setBlobBag(createTikaBlobBag(number))
                .build();

        return PipeStream.newBuilder()
                .setStreamId("tika-stream-" + UUID.randomUUID().toString())
                .setDocument(tikaDoc)
                .setCurrentNodeId("tika-parser")
                .build();
    }

    private PipeDoc createChunkerInputDocument(int number) {
        return PipeDoc.newBuilder()
                .setDocId("chunker-input-" + String.format("%03d", number))
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setTitle("Document Ready for Chunking " + number)
                        .setBody("This is a long document that needs to be chunked into smaller pieces. " +
                                "It contains multiple paragraphs and sections that will be split by the chunker module. " +
                                "Each chunk will maintain context and overlap for better semantic understanding. " +
                                "Document number " + number + " has specific characteristics for testing chunking algorithms.")
                        .setDocumentType("article")
                        .setSourceUri("processed://chunker-input/" + number)
                        .setContentLength(2000 + (number * 100))
                        .setCreationDate(createTimestamp())
                        .build())
                .build();
    }

    private PipeDoc createEmbedderInputDocument(int number) {
        return PipeDoc.newBuilder()
                .setDocId("embedder-input-" + String.format("%03d", number))
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setTitle("Chunked Document " + number)
                        .setBody("This document has been chunked and is ready for embedding generation.")
                        .setDocumentType("article")
                        .setSourceUri("processed://embedder-input/" + number)
                        .setContentLength(500 + (number * 25))
                        .setCreationDate(createTimestamp())
                        .addSemanticResults(SemanticProcessingResult.newBuilder()
                                .setResultId("chunked-result-" + number)
                                .setSourceFieldName("body")
                                .setChunkConfigId("standard-chunker-v1")
                                .setResultSetName("body_chunks_" + number)
                                .addChunks(SemanticChunk.newBuilder()
                                        .setChunkId("chunk-" + number + "-1")
                                        .setChunkNumber(1)
                                        .setEmbeddingInfo(ChunkEmbedding.newBuilder()
                                                .setTextContent("First chunk of document " + number)
                                                .setChunkId("chunk-" + number + "-1")
                                                .setOriginalCharStartOffset(0)
                                                .setOriginalCharEndOffset(100)
                                                .build())
                                        .build())
                                .addChunks(SemanticChunk.newBuilder()
                                        .setChunkId("chunk-" + number + "-2")
                                        .setChunkNumber(2)
                                        .setEmbeddingInfo(ChunkEmbedding.newBuilder()
                                                .setTextContent("Second chunk of document " + number)
                                                .setChunkId("chunk-" + number + "-2")
                                                .setOriginalCharStartOffset(80)
                                                .setOriginalCharEndOffset(180)
                                                .build())
                                        .build())
                                .build())
                        .build())

                .build();
    }

    private PipeDoc createEmbedderOutputDocument(int number) {
        return PipeDoc.newBuilder()
                .setDocId("embedder-output-" + String.format("%03d", number))
                .setSearchMetadata(SearchMetadata.newBuilder()
                        .setTitle("Embedded Document " + number)
                        .setBody("This document has embeddings generated for all chunks.")
                        .setDocumentType("article")
                        .setSourceUri("processed://embedder-output/" + number)
                        .setContentLength(500 + (number * 25))
                        .setCreationDate(createTimestamp())
                        .addSemanticResults(SemanticProcessingResult.newBuilder()
                                .setResultId("embedded-result-" + number)
                                .setSourceFieldName("body")
                                .setChunkConfigId("standard-chunker-v1")
                                .setEmbeddingConfigId("sentence-transformer-v1")
                                .setResultSetName("body_embeddings_" + number)
                                .addChunks(SemanticChunk.newBuilder()
                                        .setChunkId("chunk-" + number + "-1")
                                        .setChunkNumber(1)
                                        .setEmbeddingInfo(ChunkEmbedding.newBuilder()
                                                .setTextContent("First chunk of document " + number)
                                                .addVector(0.1f + (number * 0.01f))
                                                .addVector(0.2f + (number * 0.02f))
                                                .addVector(0.3f + (number * 0.03f))
                                                .addVector(0.4f + (number * 0.04f))
                                                .setChunkId("chunk-" + number + "-1")
                                                .setOriginalCharStartOffset(0)
                                                .setOriginalCharEndOffset(100)
                                                .build())
                                        .build())
                                .addChunks(SemanticChunk.newBuilder()
                                        .setChunkId("chunk-" + number + "-2")
                                        .setChunkNumber(2)
                                        .setEmbeddingInfo(ChunkEmbedding.newBuilder()
                                                .setTextContent("Second chunk of document " + number)
                                                .addVector(0.5f + (number * 0.01f))
                                                .addVector(0.6f + (number * 0.02f))
                                                .addVector(0.7f + (number * 0.03f))
                                                .addVector(0.8f + (number * 0.04f))
                                                .setChunkId("chunk-" + number + "-2")
                                                .setOriginalCharStartOffset(80)
                                                .setOriginalCharEndOffset(180)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

    }

    private BlobBag createTikaBlobBag(int number) {
        String originalContent = "Original binary content from PDF " + number;
        String extractedText = "Text extracted by Tika from document " + number + ". This includes all readable content.";
        
        return BlobBag.newBuilder()
                .setBlobs(Blobs.newBuilder()
                        .addBlob(Blob.newBuilder()
                                .setBlobId("original-" + number)
                                .setData(com.google.protobuf.ByteString.copyFromUtf8(originalContent))
                                .setMimeType("application/pdf")
                                .setFilename("document-" + number + ".pdf")
                                .setSizeBytes(originalContent.length())
                                .setChecksum("original-checksum-" + number)
                                .setChecksumType(ChecksumType.CHECKSUM_TYPE_SHA256)
                                .build())
                        .addBlob(Blob.newBuilder()
                                .setBlobId("extracted-text-" + number)
                                .setData(com.google.protobuf.ByteString.copyFromUtf8(extractedText))
                                .setMimeType("text/plain")
                                .setFilename("extracted-" + number + ".txt")
                                .setSizeBytes(extractedText.length())
                                .setChecksum("extracted-checksum-" + number)
                                .setChecksumType(ChecksumType.CHECKSUM_TYPE_SHA256)
                                .build())
                        .build())
                .build();
    }
}