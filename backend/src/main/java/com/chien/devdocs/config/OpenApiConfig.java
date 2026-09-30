package com.chien.devdocs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Thông tin hiển thị trên Swagger UI ({@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI devDocsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevDocs RAG Assistant API")
                        .version("v1")
                        .description("""
                                Hỏi đáp trên tài liệu học lập trình của bạn bằng RAG (Spring AI + pgvector + Ollama).
                                Luồng chính: upload tài liệu → POST /api/v1/chat. Lỗi trả về theo RFC 9457 Problem Details."""))
                .tags(List.of(
                        new Tag().name("document-controller").description("Nạp, xem, xóa, index lại tài liệu"),
                        new Tag().name("chat-controller").description("Hỏi đáp có trích dẫn nguồn"),
                        new Tag().name("evaluation-controller").description("Đo chất lượng retrieval / câu trả lời, thí nghiệm Phase 4"),
                        new Tag().name("playground-controller").description("Phase 0: gọi thử chat model và embedding")));
    }
}
