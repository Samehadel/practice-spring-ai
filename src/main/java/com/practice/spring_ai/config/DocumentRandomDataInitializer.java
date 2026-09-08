package com.practice.spring_ai.config;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "spring.ai.practice.random-data-initializer.enabled", havingValue = "true")
public class DocumentRandomDataInitializer {
    private final VectorStore vectorStore;


    @PostConstruct
    public void init() {
        List<String> documentsString = List.of(
                "The new Mac Mini with M4 chip delivers incredible performance in a compact design.",
                "Featuring the powerful M4 processor with up to 10-core CPU and 10-core GPU.",
                "The M4 chip offers blazing-fast speeds for demanding tasks and professional workflows.",
                "Unified memory options up to 32GB provide seamless multitasking capabilities.",
                "Storage options include up to 2TB SSD for ample space for applications and files.",
                "The sleek aluminum enclosure measures just 5x5 inches for minimal footprint.",
                "Perfect for any workspace with its compact and modern design aesthetic.",
                "Includes Thunderbolt 5 ports for ultra-fast external device connectivity.",
                "HDMI 2.1 support enables high-resolution display output up to 8K.",
                "Wi-Fi 7 provides the fastest wireless networking speeds available.",
                "Bluetooth 5.3 ensures reliable wireless peripheral connections.",
                "Ideal for creative professionals working with video, photo, and music production.",
                "Developers appreciate the powerful compilation and testing capabilities.",
                "Everyday users enjoy desktop-class performance in a tiny form factor.",
                "Energy-efficient design reduces power consumption while maintaining performance.",
                "Silent operation thanks to advanced thermal management system.",
                "Supports up to two external displays for expanded productivity.",
                "macOS integration provides seamless ecosystem connectivity with other Apple devices.",
                "Advanced security features include Secure Enclave and Apple Silicon protection.",
                "The Mac Mini with M4 represents the future of compact desktop computing."
        );

        List<Document> documents = documentsString.stream()
                .map(text -> new Document(stableId(text), text, Map.of()))
                .toList();

        List<String> ids = documents.stream()
                .map(Document::getId)
                .toList();

        vectorStore.delete(ids);
        vectorStore.add(documents);
    }

    private String stableId(String content) {
        return UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
