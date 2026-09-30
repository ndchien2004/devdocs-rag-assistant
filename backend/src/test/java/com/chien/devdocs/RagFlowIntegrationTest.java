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

    @Test
    @Order(6)
    void everyQuestionIsLogged() {                                            // BR-QRY-07
        Integer logs = jdbc.queryForObject("select count(*) from query_logs", Integer.class);
        Integer notFound = jdbc.queryForObject("select count(*) from query_logs where found = false", Integer.class);

        assertThat(logs).isGreaterThanOrEqualTo(3);
        assertThat(notFound).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(7)
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
    }

    @Test
    @Order(8)
    void reindexDoesNotDuplicateChunks() {                                    // TC-ING-07
        int before = chunkCount(documentId);

        client.post().uri("/api/v1/documents/{id}/reindex", documentId).exchange().expectStatus().isOk();

        assertThat(chunkCount(documentId)).isEqualTo(before);
    }

    @Test
    @Order(9)
    void deleteRemovesAllChunks() {                                           // TC-ING-06
        client.delete().uri("/api/v1/documents/{id}", documentId).exchange().expectStatus().isNoContent();

        assertThat(chunkCount(documentId)).isZero();
        client.get().uri("/api/v1/documents/{id}", documentId).exchange().expectStatus().isNotFound();
    }
}
