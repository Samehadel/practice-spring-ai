package com.practice.spring_ai.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentIngestionControllerTests {

    private VectorStore vectorStore;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        mvc = MockMvcBuilders.standaloneSetup(new DocumentIngestionController(vectorStore)).build();
    }

    @Test
    void shouldStoreSmallTextDocumentWithTenantMetadata() throws Exception {
        doAnswer(invocation -> {
            List<Document> documents = invocation.getArgument(0);
            assertThat(documents).hasSize(1);
            assertThat(documents.getFirst().getText()).isEqualTo("A receipt is required to return an item.");
            assertThat(documents.getFirst().getMetadata())
                    .containsEntry("client_id", "small-policy-demo")
                    .containsEntry("status", "active")
                    .containsEntry("filename", "receipt-policy.txt");
            return null;
        }).when(vectorStore).add(anyList());

        mvc.perform(post("/documents/text").param("clientId", "small-policy-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filename":"receipt-policy.txt","text":"A receipt is required to return an item."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunksStored").value(1))
                .andExpect(jsonPath("$.clientId").value("small-policy-demo"));
    }

    @Test
    void shouldRejectBlankText() throws Exception {
        mvc.perform(post("/documents/text").param("clientId", "small-policy-demo")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldRejectBlankClientId() throws Exception {
        mvc.perform(post("/documents/text").param("clientId", " ")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"A receipt is required.\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vectorStore);
    }
}
