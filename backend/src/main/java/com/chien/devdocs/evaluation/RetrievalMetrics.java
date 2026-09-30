package com.chien.devdocs.evaluation;

import java.util.List;

/**
 * Các chỉ số đánh giá retrieval (mục 7.4). Hàm thuần — không phụ thuộc Spring, dễ unit test.
 * <ul>
 *   <li><b>Hit@K</b>: có ít nhất một chunk đúng trong top-K hay không.</li>
 *   <li><b>Reciprocal rank</b>: 1 / vị trí của chunk đúng đầu tiên (không có = 0). MRR = trung bình các giá trị này.</li>
 * </ul>
 * Một chunk "đúng" khi file_name khớp và page_number nằm trong danh sách expected.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /** Một chunk tìm được, theo thứ tự xếp hạng. */
    public record RankedHit(String fileName, Integer pageNumber, Double score) {
    }

    public static boolean isRelevant(RankedHit hit, List<EvalQuestion.ExpectedSource> expected) {
        return expected.stream().anyMatch(e ->
                e.fileName().equals(hit.fileName()) && hit.pageNumber() != null && e.pageNumber() == hit.pageNumber());
    }

    /** Vị trí (bắt đầu từ 1) của chunk đúng đầu tiên, hoặc 0 nếu không có. */
    public static int firstRelevantRank(List<RankedHit> ranked, List<EvalQuestion.ExpectedSource> expected) {
        for (int i = 0; i < ranked.size(); i++) {
            if (isRelevant(ranked.get(i), expected)) {
                return i + 1;
            }
        }
        return 0;
    }

    public static boolean hitAt(int k, int firstRelevantRank) {
        return firstRelevantRank > 0 && firstRelevantRank <= k;
    }

    public static double reciprocalRank(int firstRelevantRank) {
        return firstRelevantRank == 0 ? 0 : 1.0 / firstRelevantRank;
    }

    /** Tỷ lệ phần trăm true, làm tròn 1 chữ số; danh sách rỗng → null (không có dữ liệu để tính). */
    public static Double rate(List<Boolean> values) {
        if (values.isEmpty()) {
            return null;
        }
        long hits = values.stream().filter(Boolean::booleanValue).count();
        return round(100.0 * hits / values.size(), 1);
    }

    public static Double mean(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
