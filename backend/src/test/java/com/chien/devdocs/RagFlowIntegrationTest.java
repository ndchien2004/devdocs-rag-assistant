package com.chien.devdocs;

import com.chien.devdocs.support.FakeAiModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test luồng ingest → query trên PostgreSQL + pgvector thật (Testcontainers).
 * Embedding và chat model là bản giả ({@link FakeAiModels}) nên không cần Ollama; tự bỏ qua nếu máy không có Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rag.similarity-threshold=0.2",
        "app.rag.storage-dir=target/it-storage",
        "spring.ai.ollama.init.pull-model-strategy=never"
})
@AutoConfigureRestTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class FakeModels {
        @Bean
        @Primary
        FakeAiModels.BagOfWordsEmbeddingModel fakeEmbeddingModel() {
            return new FakeAiModels.BagOfWordsEmbeddingModel();
        }

        @Bean
        @Primary
        FakeAiModels.CountingChatModel fakeChatModel() {
            return new FakeAiModels.CountingChatModel();
        }
    }

    private static final String SPRING_DOC = """
            # Spring Transactions

            Tài liệu về transaction trong Spring.

            ## Propagation REQUIRED và REQUIRES_NEW

            Propagation REQUIRED tham gia transaction hiện có, nếu chưa có thì tạo transaction mới.
            Propagation REQUIRES_NEW luôn tạo transaction mới và tạm dừng transaction hiện tại.

            ## Rollback rules

            Mặc định @Transactional rollback với RuntimeException, checked exception thì không rollback.
            """;

    private static String documentId;
    private static String conversationId;

    @Autowired
    RestTestClient client;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeAiModels.CountingChatModel chatModel;

    @BeforeEach
    void resetChatModel() {
        chatModel.reset("REQUIRES_NEW luôn tạo transaction mới [1].");
    }

    private RestTestClient.ResponseSpec upload(String fileName, String content, String topic) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        parts.add("topic", topic);
        return client.post().uri("/api/v1/documents").contentType(MediaType.MULTIPART_FORM_DATA).body(parts).exchange();
    }

    private JsonNode chat(Map<String, Object> body) {
        return client.post().uri("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).body(body).exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class).getResponseBody();
    }

    private int chunkCount(String docId) {
        return jdbc.queryForObject("select count(*) from vector_store where metadata->>'document_id' = ?",
                Integer.class, docId);
    }

    @Test
    @Order(1)
    void uploadIndexesChunksWithAllRequiredMetadata() {                       // TC-ING-01
        JsonNode doc = upload("02_Spring.md", SPRING_DOC, "SPRING")
                .expectStatus().isCreated()
                .returnResult(JsonNode.class).getResponseBody();

        assertThat(doc.get("status").asString()).isEqualTo("INDEXED");
        assertThat(doc.get("chunkCount").asInt()).isPositive();
        documentId = doc.get("id").asString();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select metadata::text as md from vector_store where metadata->>'document_id' = ?", documentId);
        assertThat(rows).hasSize(doc.get("chunkCount").asInt());
        assertThat(rows).allSatisfy(r -> assertThat((String) r.get("md"))
                .contains("\"document_id\"", "\"file_name\"", "\"page_number\"", "\"topic\"", "\"chunk_index\""));
    }

    @Test
    @Order(2)
    void uploadingTheSameFileAgainIs409() {                                    // TC-ING-04
        upload("copy.md", SPRING_DOC, "SPRING")
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.existingDocumentId").isEqualTo(documentId);
    }

    @Test
    @Order(3)
    void relevantQuestionIsAnsweredWithCitedSources() {                       // TC-QRY-01
        JsonNode res = chat(Map.of("question", "Propagation REQUIRES_NEW và REQUIRED khác nhau thế nào?"));

        assertThat(res.get("found").asBoolean()).isTrue();
        assertThat(res.get("answer").asString()).contains("[1]");
        assertThat(res.get("sources")).hasSize(1);
        assertThat(res.get("sources").get(0).get("fileName").asString()).isEqualTo("02_Spring.md");
        assertThat(chatModel.calls()).isEqualTo(1);
        assertThat(chatModel.lastPrompt().getContents()).contains("<context>", "REQUIRES_NEW");
    }

    @Test
    @Order(4)
    void unrelatedQuestionReturnsNotFoundWithoutCallingLlm() {                // TC-QRY-04
        JsonNode res = chat(Map.of("question", "Thời tiết Hà Nội hôm nay thế nào?"));

        assertThat(res.get("found").asBoolean()).isFalse();
        assertThat(res.get("sources")).isEmpty();
        assertThat(chatModel.calls()).isZero();
    }

    @Test
    @Order(5)
    void topicFilterExcludesOtherTopics() {                                   // TC-QRY-05
        JsonNode res = chat(Map.of("question", "Propagation REQUIRES_NEW là gì?", "topic", "REACT"));

        assertThat(res.get("found").asBoolean()).isFalse();
        assertThat(chatModel.calls()).isZero();
    }

    private String stream(Map<String, Object> body) {
        return client.post().uri("/api/v1/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).body(body).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class).getResponseBody();
    }

    @Test
    @Order(6)
    void streamingSendsSourcesThenTokensThenDone() {
        String body = stream(Map.of("question", "Propagation REQUIRES_NEW và REQUIRED khác nhau thế nào?"));

        int sources = body.indexOf("event:sources");
        int firstToken = body.indexOf("event:token");
        int done = body.indexOf("event:done");
        assertThat(sources).isNotNegative();
        assertThat(firstToken).isGreaterThan(sources);
        assertThat(done).isGreaterThan(firstToken);
        assertThat(body.split("event:token", -1).length - 1).as("one event per token").isGreaterThan(1);
        assertThat(body.substring(done)).contains("\"found\":true", "\"fileName\":\"02_Spring.md\"");
        assertThat(chatModel.calls()).isEqualTo(1);
    }

    @Test
    @Order(7)
    void streamingAnUnrelatedQuestionSendsOnlyDoneWithoutCallingLlm() {
        String body = stream(Map.of("question", "Thời tiết Hà Nội hôm nay thế nào?"));

        assertThat(body).contains("event:done", "\"found\":false").doesNotContain("event:token", "event:sources");
        assertThat(chatModel.calls()).isZero();
    }

    @Test
    @Order(8)
    void everyQuestionIsLogged() {                                            // BR-QRY-07
        Integer logs = jdbc.queryForObject("select count(*) from query_logs", Integer.class);
        Integer notFound = jdbc.queryForObject("select count(*) from query_logs where found = false", Integer.class);

        assertThat(logs).isGreaterThanOrEqualTo(3);
        assertThat(notFound).isGreaterThanOrEqualTo(2);

        chat(Map.of("question", "Propagation REQUIRES_NEW là gì?", "mode", "RAG_ADVISOR"));
        Integer advisorLogs = jdbc.queryForObject(
                "select count(*) from query_logs where mode = 'RAG_ADVISOR' and retrieval_ms is null", Integer.class);
        assertThat(advisorLogs).isEqualTo(1);
    }

    @Test
    @Order(9)
    void chunkingExperimentIndexesIntoASeparateTable() {                      // Phase 4, E1
        JsonNode report = client.post().uri("/api/v1/evaluation/experiments/chunking")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("chunkSize", 60))
                .exchange().expectStatus().isOk()
                .returnResult(JsonNode.class).getResponseBody();

        assertThat(report.get("config").get("label").asString()).startsWith("TOKEN chunk=60 overlap=0");
        assertThat(report.get("summary").get("avgLatencyMs")).isNotNull();
        Integer rows = jdbc.queryForObject("select count(*) from exp_token_60_o0", Integer.class);
        assertThat(rows).isPositive();
        Integer foreignRows = jdbc.queryForObject(
                "select count(*) from exp_token_60_o0 where metadata->>'document_id' <> ?", Integer.class, documentId);
        assertThat(foreignRows).isZero();
        assertThat(chunkCount(documentId)).as("main vector_store untouched").isPositive();

        JsonNode dropped = client.delete().uri("/api/v1/evaluation/experiments").exchange()
                .expectStatus().isOk()
                .returnResult(JsonNode.class).getResponseBody();
        assertThat(dropped.get("dropped").toString()).contains("exp_token_60_o0");
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'exp_token_60_o0'", Integer.class);
        assertThat(tables).isZero();
        assertThat(chunkCount(documentId)).as("main vector_store untouched").isPositive();
    }

    @Test
    @Order(10)
    void conversationPersistsMessagesAndScopeAndSupportsFollowUps() {
        String isolatedText = "# POLYCARBONATE VECTOR ISOLATION\n\nPOLYCARBONATE VECTOR ISOLATION uses the ZEBRACOLOR material identifier exclusively.";
        JsonNode otherDoc = upload("isolation.md", isolatedText, "REACT")
                .expectStatus().isCreated().returnResult(JsonNode.class).getResponseBody();
        assertThat(otherDoc.get("id").asString()).isNotEqualTo(documentId);
        String isolatedQuestion = "POLYCARBONATE VECTOR ISOLATION ZEBRACOLOR";
        chatModel.reset("The selected material is documented [1].");
        assertThat(chat(Map.of("question", isolatedQuestion)).get("found").asBoolean()).isTrue();
        chatModel.reset("The selected material is documented [1].");
        JsonNode scoped = client.post().uri("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("documentIds", List.of(documentId))).exchange()
                .expectStatus().isCreated().returnResult(JsonNode.class).getResponseBody();
        client.post().uri("/api/v1/conversations/{id}/messages", scoped.get("id").asString())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("question", isolatedQuestion)).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.messages[1].found").isEqualTo(false);
        assertThat(chatModel.calls()).isZero();
        client.delete().uri("/api/v1/conversations/{id}", scoped.get("id").asString()).exchange().expectStatus().isNoContent();

        JsonNode created = client.post().uri("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("documentIds", List.of(documentId))).exchange()
                .expectStatus().isCreated().returnResult(JsonNode.class).getResponseBody();
        conversationId = created.get("id").asString();

        JsonNode first = client.post().uri("/api/v1/conversations/{id}/messages", conversationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("question", "Propagation REQUIRES_NEW và REQUIRED khác nhau thế nào?", "topic", "SPRING"))
                .exchange().expectStatus().isOk().returnResult(JsonNode.class).getResponseBody();
        assertThat(first.get("documentIds").get(0).asString()).isEqualTo(documentId);
        assertThat(first.get("messages")).hasSize(2);
        assertThat(first.get("messages").get(1).get("sources").get(0).get("documentId").asString()).isEqualTo(documentId);

        client.post().uri("/api/v1/conversations/{id}/messages", conversationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("question", "Còn REQUIRED thì sao?"))
                .exchange().expectStatus().isOk();
        assertThat(chatModel.calls()).isEqualTo(2);
        assertThat(chatModel.lastPrompt().getContents()).contains("Người dùng: Propagation REQUIRES_NEW");

        client.patch().uri("/api/v1/conversations/{id}", conversationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("title", "Ôn transaction"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.title").isEqualTo("Ôn transaction");
        client.get().uri("/api/v1/conversations/{id}", conversationId).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.messages.length()").isEqualTo(4);

        client.put().uri("/api/v1/conversations/{id}/scope", conversationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("documentIds", List.of())).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.documentIds.length()").isEqualTo(0);
        int calls = chatModel.calls();
        client.post().uri("/api/v1/conversations/{id}/messages", conversationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("question", "Hỏi trong scope rỗng"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.messages[5].found").isEqualTo(false);
        assertThat(chatModel.calls()).isEqualTo(calls);
    }

    @Test
    @Order(11)
    void conversationStreamingSavesBothMessagesBeforeDone() {
        String id = client.post().uri("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("documentIds", List.of(documentId))).exchange()
                .expectStatus().isCreated().returnResult(JsonNode.class).getResponseBody().get("id").asString();

        String body = client.post().uri("/api/v1/conversations/{id}/messages/stream", id)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .body(Map.of("question", "Propagation REQUIRES_NEW và REQUIRED khác nhau thế nào?")).exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        assertThat(body).contains("event:sources", "event:token", "event:done", "\"found\":true");
        client.get().uri("/api/v1/conversations/{id}", id).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.messages.length()").isEqualTo(2)
                .jsonPath("$.messages[1].found").isEqualTo(true)
                .jsonPath("$.messages[1].sources[0].documentId").isEqualTo(documentId);
        // Hội thoại không tồn tại → 404 thường, chưa mở stream.
        client.post().uri("/api/v1/conversations/{id}/messages/stream", java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("question", "x")).exchange()
                .expectStatus().isNotFound();

        client.delete().uri("/api/v1/conversations/{id}", id).exchange().expectStatus().isNoContent();
    }

    @Test
    @Order(12)
    void concurrentQuestionsInOneConversationAreAllPersistedInOrder() {
        String id = client.post().uri("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("documentIds", List.of(documentId))).exchange()
                .expectStatus().isCreated().returnResult(JsonNode.class).getResponseBody().get("id").asString();

        // sequence_no tính từ số tin nhắn hiện có: không khóa dòng thì các lượt hỏi đồng thời đụng unique index → 500.
        int parallel = 4;
        List<CompletableFuture<HttpStatusCode>> asks = IntStream.range(0, parallel)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> client.post()
                        .uri("/api/v1/conversations/{id}/messages", id).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("question", "Propagation REQUIRES_NEW và REQUIRED khác nhau thế nào? (" + i + ")"))
                        .exchange().returnResult(JsonNode.class).getStatus()))
                .toList();

        assertThat(asks).extracting(CompletableFuture::join).containsOnly(HttpStatus.OK);
        List<Integer> sequence = jdbc.queryForList(
                "select sequence_no from conversation_messages where conversation_id = ?::uuid order by sequence_no",
                Integer.class, id);
        assertThat(sequence).containsExactlyElementsOf(IntStream.range(0, parallel * 2).boxed().toList());
        List<String> roles = jdbc.queryForList(
                "select role from conversation_messages where conversation_id = ?::uuid order by sequence_no",
                String.class, id);
        assertThat(roles).containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT", "USER", "ASSISTANT", "USER", "ASSISTANT");

        client.delete().uri("/api/v1/conversations/{id}", id).exchange().expectStatus().isNoContent();
    }

    @Test
    @Order(13)
    void reindexDoesNotDuplicateChunks() {                                    // TC-ING-07
        int before = chunkCount(documentId);

        client.post().uri("/api/v1/documents/{id}/reindex", documentId).exchange().expectStatus().isOk();

        assertThat(chunkCount(documentId)).isEqualTo(before);
    }

    @Test
    @Order(14)
    void deleteRemovesAllChunks() {                                           // TC-ING-06
        client.delete().uri("/api/v1/documents/{id}", documentId).exchange().expectStatus().isNoContent();

        assertThat(chunkCount(documentId)).isZero();
        client.get().uri("/api/v1/documents/{id}", documentId).exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/conversations/{id}", conversationId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.documentIds.length()").isEqualTo(0)
                .jsonPath("$.messages.length()").isEqualTo(6)
                .jsonPath("$.messages[1].sources[0].fileName").isEqualTo("02_Spring.md");
    }
}
