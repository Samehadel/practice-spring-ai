package com.practice.spring_ai.data;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class DocumentDataLoader {
    private static final Logger log = LoggerFactory.getLogger(DocumentDataLoader.class);

    private final VectorStore vectorStore;

    @Value("classpath:/documents/Future_of_Jobs_Report.pdf")
    private Resource futureOfJobsReport;

    @PostConstruct
    public void readPDF() {
        DocumentReader tikaReader = new TikaDocumentReader(futureOfJobsReport);

        log.info("Reading PDF: {}", futureOfJobsReport.getFilename());
        List<Document> rawDocuments = tikaReader.get();
        log.info("PDF {}read complete.", futureOfJobsReport.getFilename());

        TextSplitter splitter = new TokenTextSplitter(
                800,   // chunk size in tokens
                350,   // min chunk size to merge with next
                5,     // min chunk length to keep
                10000, // max number of chunks
                true   // keep separators
        );
        List<Document> chunks = splitter.apply(rawDocuments);

        log.info("Adding {} chunks to vector store.", chunks.size());

        vectorStore.add(chunks);
    }
}
