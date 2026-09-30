package com.chien.devdocs.document;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Splitter cửa sổ trượt có overlap (thí nghiệm E4, Phase 4).
 * <p>
 * Mỗi chunk tối đa {@code chunkSize} token; chunk sau lặp lại khoảng {@code overlap} token cuối của chunk trước,
 * để một ý nằm ngay ranh giới vẫn xuất hiện trọn vẹn trong ít nhất một chunk.
 * <p>
 * Cắt theo ranh giới <b>từ</b> (khoảng trắng), không cắt giữa token: cắt giữa token BPE có thể làm vỡ ký tự
 * tiếng Việt nhiều byte. Token được đếm bằng cùng bộ mã hóa với {@code TokenTextSplitter} (cl100k_base).
 */
public class OverlapTextSplitter extends TextSplitter {

    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final int chunkSize;
    private final int overlap;

    public OverlapTextSplitter(int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Require 0 <= overlap < chunkSize, got chunkSize=" + chunkSize
                    + ", overlap=" + overlap);
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    protected List<String> splitText(String text) {
        // Giữ khoảng trắng đi kèm mỗi từ để ghép lại đúng như văn bản gốc (kể cả xuống dòng, thụt lề code).
        List<String> words = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\S+\\s*").matcher(text);
        while (m.find()) {
            words.add(m.group());
        }
        int[] tokens = words.stream().mapToInt(w -> Math.max(1, ENCODING.countTokens(w))).toArray();

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < words.size()) {
            int end = start;
            int size = 0;
            while (end < words.size() && (size + tokens[end] <= chunkSize || end == start)) {
                size += tokens[end];
                end++;
            }
            chunks.add(String.join("", words.subList(start, end)).strip());
            if (end >= words.size()) {
                break;
            }
            // Lùi lại khoảng `overlap` token cho chunk kế tiếp, nhưng luôn tiến ít nhất 1 từ.
            int back = end;
            int overlapTokens = 0;
            while (back > start + 1 && overlapTokens + tokens[back - 1] <= overlap) {
                back--;
                overlapTokens += tokens[back];
            }
            start = back;
        }
        return chunks;
    }
}
