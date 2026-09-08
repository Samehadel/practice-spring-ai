package com.practice.spring_ai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Log4j2
public class DocumentDataLoader {
    private final VectorStore vectorStore;

    @Value("classpath:/documents/Future_of_Jobs_Report.pdf")
    private Resource futureOfJobsReport;


    @PostConstruct
    public void readPDF() {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(futureOfJobsReport,
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfBottomTextLinesToDelete(3)
                                .withNumberOfTopPagesToSkipBeforeDelete(1)
                                .build())
                        .withPagesPerDocument(0)
                        .build());


        log.info("Reading PDF: {}", futureOfJobsReport.getFilename());
        List<Document> rawDocuments = pdfReader.get();
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
