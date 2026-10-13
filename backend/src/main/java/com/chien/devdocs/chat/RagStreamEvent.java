package com.chien.devdocs.chat;

import com.chien.devdocs.chat.dto.ChatResponse;
import com.chien.devdocs.chat.dto.SourceDto;
import org.springframework.http.ProblemDetail;

import java.util.List;

/**
 * Một sự kiện trong luồng trả lời streaming ({@code POST /api/v1/chat/stream}).
 * Controller chuyển mỗi sự kiện thành một SSE: {@code event: <name()>}, {@code data: <JSON của data()>}.
 * <p>
 * Thứ tự: {@code sources} → nhiều {@code token} → {@code done}; hoặc chỉ {@code done} khi không có chunk nào;
 * hoặc kết thúc bằng {@code error} nếu LLM lỗi giữa chừng.
 */
public sealed interface RagStreamEvent {

    String name();

    Object data();

    /** Các chunk trong context, đánh số [1]..[n] — để frontend biến [n] thành link ngay khi đang stream. */
    record Sources(List<SourceDto> sources) implements RagStreamEvent {
        public String name() {
            return "sources";
        }

        public Object data() {
            return sources;
        }
    }

    /** Một mẩu câu trả lời. Bọc trong JSON để giữ nguyên dấu cách đầu và ký tự xuống dòng. */
    record Token(String text) implements RagStreamEvent {
        public String name() {
            return "token";
        }

        public Object data() {
            return this;
        }
    }

    /** Kết quả cuối — giống hệt body của {@code POST /api/v1/chat} (chỉ nguồn được trích dẫn). */
    record Done(ChatResponse response) implements RagStreamEvent {
        public String name() {
            return "done";
        }

        public Object data() {
            return response;
        }
    }

    /** Lỗi xảy ra sau khi stream đã bắt đầu (HTTP 200 đã gửi đi), dạng Problem Details. */
    record Failed(ProblemDetail problem) implements RagStreamEvent {
        public String name() {
            return "error";
        }

        public Object data() {
            return problem;
        }
    }
}
