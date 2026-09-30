package com.chien.devdocs.chat;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Lớp phòng thủ thứ hai cho BR-QRY-06 (lớp thứ nhất là system prompt + thẻ &lt;context&gt;).
 * <p>
 * Lược bỏ những dòng trong chunk có dạng chỉ thị prompt injection phổ biến trước khi đưa vào prompt.
 * Lý do cần: với qwen2.5:3b, system prompt chặn được việc model <i>làm theo</i> chỉ thị, nhưng model đôi khi vẫn
 * <i>chép</i> nguyên câu độc hại vào câu trả lời (1/10 lần trong kiểm thử TC-QRY-08).
 * <p>
 * Đây là heuristic theo mẫu câu — không chặn được mọi biến thể tấn công.
 */
public final class PromptInjectionGuard {

    static final String REDACTED = "[đã lược bỏ một dòng có dạng chỉ thị]";

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("\\b(ignore|disregard|forget)\\b.{0,30}\\b(previous|prior|above|earlier|all)\\b.{0,20}"
                    + "\\b(instructions?|prompts?|rules?)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(reveal|print|show)\\b.{0,20}\\bsystem prompt\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(bỏ qua|phớt lờ|quên)\\s.{0,30}(hướng dẫn|chỉ thị|quy tắc)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));

    private PromptInjectionGuard() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(isSuspicious(line) ? REDACTED : line);
        }
        return out.toString();
    }

    public static boolean isSuspicious(String line) {
        return PATTERNS.stream().anyMatch(p -> p.matcher(line).find());
    }
}
