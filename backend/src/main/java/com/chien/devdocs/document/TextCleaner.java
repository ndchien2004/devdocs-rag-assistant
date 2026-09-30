package com.chien.devdocs.document;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Làm sạch văn bản trước khi cắt chunk (BR-ING-04).
 * <ul>
 *   <li>Chuẩn hóa Unicode về NFC (cùng chữ "ệ" nhưng khác byte sẽ làm embedding lệch).</li>
 *   <li>Xóa số trang đứng một mình và header/footer lặp lại trên nhiều trang.</li>
 *   <li>Gộp khoảng trắng / dòng trống liên tiếp — trừ bên trong code block Markdown (```), được giữ nguyên.</li>
 *   <li>Không đụng vào ký tự khác: annotation ({@code @Transactional}), ký hiệu trong code được giữ nguyên.</li>
 * </ul>
 */
@Component
public class TextCleaner {

    /** "12", "- 12 -", "Page 12", "Trang 12", "12 / 300", "Page 3 of 10". */
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile(
            "^\\s*(?:page|trang)?\\s*[-–—]?\\s*\\d{1,4}\\s*(?:(?:/|of|trên)\\s*\\d{1,4})?\\s*[-–—]?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INNER_SPACES = Pattern.compile("(?<=\\S)[ \\t\\u00A0]{2,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** Chỉ xét vài dòng đầu/cuối mỗi trang khi tìm header/footer. */
    private static final int EDGE_LINES = 2;
    /** Cần ít nhất ngần này trang thì mới đoán header/footer lặp lại. */
    private static final int MIN_PAGES_FOR_REPEAT_DETECTION = 3;
    private static final int MAX_HEADER_LENGTH = 120;

    /**
     * Làm sạch danh sách trang (PDF: 1 Document / trang), bỏ trang có ít hơn {@code minChars} ký tự sau khi làm sạch.
     * Metadata của từng trang được giữ nguyên.
     */
    public List<Document> cleanPages(List<Document> pages, int minChars) {
        List<String> texts = pages.stream().map(d -> normalize(d.getText())).toList();
        Set<String> repeated = findRepeatedEdgeLines(texts);

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String cleaned = clean(removeEdgeLines(texts.get(i), repeated));
            if (cleaned.length() >= minChars) {
                result.add(pages.get(i).mutate().text(cleaned).build());
            }
        }
        return result;
    }

    /** Làm sạch một đoạn văn bản độc lập (không có ngữ cảnh các trang khác). */
    public String clean(String text) {
        String normalized = dedent(normalize(text));
        StringBuilder out = new StringBuilder(normalized.length());
        boolean inCodeBlock = false;
        boolean previousBlank = true; // bỏ dòng trống ở đầu văn bản

        for (String line : normalized.split("\n", -1)) {
            if (line.strip().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                out.append(line.stripTrailing()).append('\n');
                previousBlank = false;
                continue;
            }
            if (inCodeBlock) {
                out.append(line).append('\n');
                continue;
            }
            if (PAGE_NUMBER_LINE.matcher(line).matches()) {
                continue;
            }
            String collapsed = INNER_SPACES.matcher(line.stripTrailing()).replaceAll(" ");
            boolean blank = collapsed.isBlank();
            if (blank && previousBlank) {
                continue;
            }
            out.append(blank ? "" : collapsed).append('\n');
            previousBlank = blank;
        }
        return out.toString().strip();
    }

    /**
     * Bỏ phần thụt lề chung của mọi dòng (PDF reader giữ layout nên mỗi dòng có lề trái dài),
     * nhưng giữ thụt lề tương đối — code trong PDF vẫn đúng cấu trúc.
     */
    private static String dedent(String text) {
        int common = text.lines()
                .filter(l -> !l.isBlank())
                .mapToInt(TextCleaner::leadingWhitespace)
                .min().orElse(0);
        if (common == 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            out.append(line.isBlank() ? "" : line.substring(common)).append('\n');
        }
        return out.toString();
    }

    private static int leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t' || line.charAt(i) == ' ')) {
            i++;
        }
        return i;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        return CONTROL_CHARS.matcher(nfc.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("");
    }

    /**
     * Header/footer = dòng ngắn nằm ở mép trên/dưới, xuất hiện trên ≥ 50% số trang.
     * Chữ số được thay bằng '#' để "Spring Guide — 12" và "Spring Guide — 13" được coi là cùng một dòng.
     */
    Set<String> findRepeatedEdgeLines(List<String> pageTexts) {
        if (pageTexts.size() < MIN_PAGES_FOR_REPEAT_DETECTION) {
            return Set.of();
        }
        Map<String, Integer> pagesContaining = new HashMap<>();
        for (String text : pageTexts) {
            Set<String> keysOnPage = new HashSet<>();
            for (String line : edgeLines(text)) {
                keysOnPage.add(key(line));
            }
            keysOnPage.forEach(k -> pagesContaining.merge(k, 1, Integer::sum));
        }
        int threshold = Math.max(MIN_PAGES_FOR_REPEAT_DETECTION, (pageTexts.size() + 1) / 2);
        Set<String> repeated = new HashSet<>();
        pagesContaining.forEach((k, count) -> {
            if (count >= threshold) {
                repeated.add(k);
            }
        });
        return repeated;
    }

    private String removeEdgeLines(String text, Set<String> repeatedKeys) {
        if (repeatedKeys.isEmpty()) {
            return text;
        }
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        stripEdge(lines, repeatedKeys, true);
        stripEdge(lines, repeatedKeys, false);
        return String.join("\n", lines);
    }

    private void stripEdge(List<String> lines, Set<String> repeatedKeys, boolean top) {
        int checked = 0;
        int i = top ? 0 : lines.size() - 1;
        while (i >= 0 && i < lines.size() && checked < EDGE_LINES) {
            String line = lines.get(i);
            if (line.isBlank()) {
                i += top ? 1 : -1;
                continue;
            }
            checked++;
            if (line.strip().length() <= MAX_HEADER_LENGTH && repeatedKeys.contains(key(line))) {
                lines.remove(i);
                if (!top) {
                    i--;
                }
            } else {
                i += top ? 1 : -1;
            }
        }
    }

    private List<String> edgeLines(String text) {
        List<String> nonBlank = text.lines().filter(l -> !l.isBlank()).toList();
        List<String> edges = new ArrayList<>();
        for (int i = 0; i < nonBlank.size(); i++) {
            boolean atEdge = i < EDGE_LINES || i >= nonBlank.size() - EDGE_LINES;
            // strip(): PDF reader giữ layout nên đệm dấu cách tới hết bề rộng trang.
            if (atEdge && nonBlank.get(i).strip().length() <= MAX_HEADER_LENGTH) {
                edges.add(nonBlank.get(i));
            }
        }
        return edges;
    }

    private static String key(String line) {
        return DIGITS.matcher(line.strip().toLowerCase()).replaceAll("#");
    }
}
