package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatRequest;
import com.chien.devdocs.chat.dto.SourceDto;
import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.config.RagProperties;
import com.chien.devdocs.document.ChunkMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Phase 4 — RAG bằng Advisor có sẵn của Spring AI, để so sánh với RAG thủ công ({@link RagService}).
 * <p>
 * Advisor giống một AOP interceptor quanh lời gọi LLM: nó chặn request, tự gọi VectorStore, nhét context vào prompt,
 * rồi mới chuyển cho model. Code gọn hơn nhiều, nhưng — khác {@link RagService} — nó vẫn gọi LLM kể cả khi
 * không tìm thấy chunk nào.
 */
@Service
public class AdvisorRagService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorRagService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PromptBuilder promptBuilder;
    private final SourceMapper sourceMapper;
    private final RagProperties props;
    private final Resource systemPrompt;
    private final String userTemplate;

    public AdvisorRagService(ChatClient chatClient, VectorStore vectorStore, PromptBuilder promptBuilder,
                             SourceMapper sourceMapper, RagProperties props,
                             @Value("classpath:prompts/rag-system.st") Resource systemPrompt,
                             @Value("classpath:prompts/rag-user.st") Resource userPrompt) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.promptBuilder = promptBuilder;
        this.sourceMapper = sourceMapper;
        this.props = props;
        this.systemPrompt = systemPrompt;
        // ContextualQueryAugmenter dùng biến {query} thay vì {question}.
        this.userTemplate = read(userPrompt).replace("{question}", "{query}");
    }

    public RagAnswer answer(ChatRequest request, RagMode mode) {
        long start = System.currentTimeMillis();
        String question = RagService.normalize(request.question());
        int topK = request.topKOrDefault(props.topK());
        Filter.Expression filter = topicFilter(request.topic());

        ChatClientResponse response = switch (mode) {
            case QA_ADVISOR -> call(chatClient.prompt()
                    .system("Trả lời bằng tiếng Việt.")
                    .user(question)
                    .advisors(qaAdvisor(topK, filter)));
            case RAG_ADVISOR -> call(chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .advisors(ragAdvisor(topK, filter)));
            case MANUAL -> throw new IllegalArgumentException("MANUAL mode is handled by RagService");
        };

        String answer = response.chatResponse() == null || response.chatResponse().getResult() == null
                ? "" : response.chatResponse().getResult().getOutput().getText();
        answer = answer == null ? "" : answer.strip();
        List<Document> retrieved = retrievedDocuments(response, mode);
        boolean found = !RefusalDetector.isRefusal(answer);

        List<SourceDto> sources = List.of();
        if (found && !retrieved.isEmpty()) {
            // RAG_ADVISOR đánh số context bằng chính PromptBuilder → map [n] như RAG thủ công.
            // QA_ADVISOR không đánh số → không có trích dẫn, trả toàn bộ chunk đã dùng.
            List<Document> numbered = mode == RagMode.RAG_ADVISOR
                    ? promptBuilder.buildContext(retrieved, props.maxContextTokens()).chunks()
                    : retrieved;
            sources = sourceMapper.toSources(numbered, answer);
        }
        long total = System.currentTimeMillis() - start;
        log.info("{}: {} chunks → found={} in {} ms", mode, retrieved.size(), found, total);
        return new RagAnswer(answer, found, sources, total, true, retrieved.size());
    }

    private Advisor qaAdvisor(int topK, Filter.Expression filter) {
        SearchRequest.Builder search = SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(props.similarityThreshold());
        if (filter != null) {
            search.filterExpression(filter);
        }
        return QuestionAnswerAdvisor.builder(vectorStore).searchRequest(search.build()).build();
    }

    private Advisor ragAdvisor(int topK, Filter.Expression filter) {
        VectorStoreDocumentRetriever.Builder retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(topK)
                .similarityThreshold(props.similarityThreshold());
        if (filter != null) {
            retriever.filterExpression(filter);
        }
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever.build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(new PromptTemplate(userTemplate))
                        .documentFormatter(docs -> promptBuilder.buildContext(docs, props.maxContextTokens()).text())
                        .allowEmptyContext(false)
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Document> retrievedDocuments(ChatClientResponse response, RagMode mode) {
        String key = mode == RagMode.QA_ADVISOR
                ? QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS
                : RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;
        Object docs = response.context().get(key);
        return docs instanceof List<?> list ? (List<Document>) list : List.of();
    }

    private static ChatClientResponse call(ChatClient.ChatClientRequestSpec spec) {
        try {
            return spec.call().chatClientResponse();
        } catch (RuntimeException e) {
            if (AiServiceUnavailableException.isConnectivityProblem(e)) {
                throw new AiServiceUnavailableException(e);
            }
            throw e;
        }
    }

    private static Filter.Expression topicFilter(Topic topic) {
        return topic == null ? null : new FilterExpressionBuilder().eq(ChunkMetadata.TOPIC, topic.name()).build();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
