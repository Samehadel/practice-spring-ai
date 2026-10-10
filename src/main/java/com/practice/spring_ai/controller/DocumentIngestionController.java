package com.practice.spring_ai.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/documents")
public class DocumentIngestionController {

    private static final String CLIENT_ID_METADATA_KEY = "client_id";
    private static final String STATUS_METADATA_KEY = "status";
    private static final String ACTIVE_STATUS = "active";

    private final VectorStore vectorStore;

    public DocumentIngestionController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentIngestionResponse ingestPdf(@RequestParam String clientId, @RequestParam MultipartFile file) {
        validateRequest(clientId, file);

        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "uploaded.pdf";
        List<Document> rawDocuments = readPdf(file, filename);
        List<Document> chunks = splitDocuments(rawDocuments);
        List<Document> tenantChunks = addTenantMetadata(chunks, clientId.trim(), filename);

        vectorStore.add(tenantChunks);

        return new DocumentIngestionResponse(clientId.trim(), filename, tenantChunks.size());
    }

    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentIngestionResponse ingestText(@RequestParam String clientId,
                                               @RequestBody TextDocumentRequest request) {
        if (!StringUtils.hasText(clientId)) {
            throw new ResponseStatusException(BAD_REQUEST, "clientId is required");
        }
        if (!StringUtils.hasText(request.text())) {
            throw new ResponseStatusException(BAD_REQUEST, "Document text is required");
        }
        String filename = StringUtils.hasText(request.filename()) ? request.filename().trim() : "document.txt";
        List<Document> chunks = splitDocuments(List.of(new Document(request.text().trim())));
        List<Document> tenantChunks = addTenantMetadata(chunks, clientId.trim(), filename);
        vectorStore.add(tenantChunks);
        return new DocumentIngestionResponse(clientId.trim(), filename, tenantChunks.size());
    }

    private void validateRequest(String clientId, MultipartFile file) {
        if (!StringUtils.hasText(clientId)) {
            throw new ResponseStatusException(BAD_REQUEST, "clientId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "PDF file is required");
        }
        if (!MediaType.APPLICATION_PDF_VALUE.equals(file.getContentType())) {
            throw new ResponseStatusException(BAD_REQUEST, "Only PDF files are supported");
        }
    }

    private List<Document> readPdf(MultipartFile file, String filename) {
        try {
            DocumentReader reader = new TikaDocumentReader(new MultipartFileResource(file, filename));
            return reader.get();
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not read uploaded PDF", exception);
        }
    }

    private List<Document> splitDocuments(List<Document> rawDocuments) {
        TextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        return splitter.apply(rawDocuments);
    }

    private List<Document> addTenantMetadata(List<Document> chunks, String clientId, String filename) {
        return chunks.stream()
                .map(chunk -> {
                    Map<String, Object> metadata = chunk.getMetadata();
                    metadata.put(CLIENT_ID_METADATA_KEY, clientId);
                    metadata.put(STATUS_METADATA_KEY, ACTIVE_STATUS);
                    metadata.put("filename", filename);

                    String stableId = stableId(clientId, filename, chunk.getText());
                    return new Document(stableId, chunk.getText(), metadata);
                })
                .toList();
    }

    private String stableId(String clientId, String filename, String content) {
        String idSource = clientId + ":" + filename + ":" + content;
        return UUID.nameUUIDFromBytes(idSource.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record DocumentIngestionResponse(String clientId, String filename, int chunksStored) {
    }

    public record TextDocumentRequest(String filename, String text) {
    }

    private static final class MultipartFileResource extends ByteArrayResource {

        private final String filename;

        private MultipartFileResource(MultipartFile file, String filename) throws IOException {
            super(file.getBytes());
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }
}
