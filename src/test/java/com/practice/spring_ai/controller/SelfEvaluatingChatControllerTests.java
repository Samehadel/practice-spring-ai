package com.practice.spring_ai.controller;

import com.practice.spring_ai.exception.InvalidLlmResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SelfEvaluatingChatControllerTests {

    private ChatClient client;
    private FactCheckingEvaluator evaluator;
    private SelfEvaluatingChatController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        evaluator = mock(FactCheckingEvaluator.class);
        controller = new SelfEvaluatingChatController(client, evaluator);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnValidatedAnswerUsingTheExactRetrievedContext() throws Exception {
        List<Document> documents = List.of(new Document("A receipt is required."));
        stubResponse("A receipt is required.", documents);
        when(evaluator.evaluate(any())).thenAnswer(invocation -> {
            EvaluationRequest request = invocation.getArgument(0);
            assertThat(request.getUserText()).isEqualTo("Is a receipt required?");
            assertThat(request.getDataList()).isSameAs(documents);
            assertThat(request.getResponseContent()).isEqualTo("A receipt is required.");
            return new EvaluationResponse(true, "", Map.of());
        });

        mvc.perform(post("/self-evaluating-chat").param("clientId", "customer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Is a receipt required?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("A receipt is required."));
    }

    @Test
    void shouldReturnBadGatewayInsteadOfRejectedAnswer() throws Exception {
        stubResponse("Unsupported answer", List.of(new Document("A receipt is required.")));
        when(evaluator.evaluate(any())).thenReturn(new EvaluationResponse(false, "", Map.of()));

        mvc.perform(post("/self-evaluating-chat").param("clientId", "customer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Is a receipt required?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(InvalidLlmResponseException.class))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("Unsupported answer"));
    }

    @Test
    void shouldRejectMissingContextWithoutCallingJudge() {
        stubResponse("An answer", List.of());
        assertThatThrownBy(() -> controller.chat(prompt(), "customer-1"))
                .isInstanceOf(InvalidLlmResponseException.class);
        verifyNoInteractions(evaluator);
    }

    @Test
    void shouldRejectBlankAnswerWithoutCallingJudge() {
        stubResponse("   ", List.of(new Document("A receipt is required.")));
        assertThatThrownBy(() -> controller.chat(prompt(), "customer-1"))
                .isInstanceOf(InvalidLlmResponseException.class);
        verifyNoInteractions(evaluator);
    }

    @Test
    void shouldKeepJudgeFailureSeparateFromAnInvalidAnswer() {
        stubResponse("A receipt is required.", List.of(new Document("A receipt is required.")));
        IllegalStateException unavailable = new IllegalStateException("Judge unavailable");
        when(evaluator.evaluate(any())).thenThrow(unavailable);
        assertThatThrownBy(() -> controller.chat(prompt(), "customer-1")).isSameAs(unavailable);
    }

    @Test
    void shouldEvaluateExactlyYesEvenWhenModelAnswersNo() throws Exception {
        List<Document> documents = List.of(new Document("A receipt is required."));
        stubResponse("No, a receipt is not required.", documents);
        when(evaluator.evaluate(any())).thenAnswer(invocation -> {
            EvaluationRequest request = invocation.getArgument(0);
            assertThat(request.getResponseContent()).isEqualTo("Yes");
            assertThat(request.getDataList()).isSameAs(documents);
            return new EvaluationResponse(true, "", Map.of());
        });

        mvc.perform(post("/self-evaluating-chat/incorrect").param("clientId", "customer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Is a receipt required?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Yes"));
    }

    @Test
    void shouldStillRejectTheFixedYesCandidateWhenEvaluationFails() {
        stubResponse("No", List.of(new Document("Sale items cannot be returned.")));
        when(evaluator.evaluate(any())).thenAnswer(invocation -> {
            EvaluationRequest request = invocation.getArgument(0);
            assertThat(request.getResponseContent()).isEqualTo("Yes");
            return new EvaluationResponse(false, "", Map.of());
        });
        BasicPrompt question = new BasicPrompt();
        question.setQuestion("Can I return a sale item?");
        assertThatThrownBy(() -> controller.chatWithIncorrectResponse(question, "customer-1"))
                .isInstanceOf(InvalidLlmResponseException.class);
    }

    private void stubResponse(String answer, List<Document> documents) {
        ChatResponse response = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(answer))))
                .metadata(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, documents)
                .build();
        when(client.prompt().user(any(String.class))
                .advisors(org.mockito.ArgumentMatchers.<java.util.function.Consumer<ChatClient.AdvisorSpec>>any())
                .call().chatResponse()).thenReturn(response);
    }

    private BasicPrompt prompt() {
        BasicPrompt prompt = new BasicPrompt();
        prompt.setQuestion("Is a receipt required?");
        return prompt;
    }
}
