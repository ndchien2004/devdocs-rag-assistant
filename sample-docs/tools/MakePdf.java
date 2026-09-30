import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sinh PDF mẫu từ file text (sample-docs/src/*.txt). Chạy bằng: sample-docs/tools/make-pdfs.sh
 *
 * Định dạng nguồn:
 *   - Dòng đầu tiên: tiêu đề tài liệu (in ở header mỗi trang).
 *   - Mỗi trang bắt đầu bằng dòng "=== PAGE ===". Dòng tiếp theo là tiêu đề trang (in đậm).
 *   - Dòng bắt đầu bằng 4 dấu cách được in bằng font Courier (code), không tự xuống dòng.
 *
 * Mỗi trang có header và footer lặp lại — cố ý, để kiểm tra TextCleaner loại bỏ header/footer.
 */
public class MakePdf {

    private static final float MARGIN = 60;
    private static final float FONT_SIZE = 11;
    private static final float LEADING = 15;
    private static final int WRAP_CHARS = 92;

    public static void main(String[] args) throws IOException {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        List<String> lines = Files.readAllLines(in);
        String docTitle = lines.getFirst();

        List<List<String>> pages = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.equals("=== PAGE ===")) {
                pages.add(new ArrayList<>());
            } else if (!pages.isEmpty()) {
                pages.getLast().add(line);
            }
        }

        var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        var mono = new PDType1Font(Standard14Fonts.FontName.COURIER);
        var italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument pdf = new PDDocument()) {
            for (int p = 0; p < pages.size(); p++) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                float top = page.getMediaBox().getHeight() - MARGIN;
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    write(cs, italic, 9, MARGIN, top + 25, "DevDocs Sample Notes - " + docTitle);
                    write(cs, italic, 9, MARGIN, MARGIN - 25, docTitle + " | page " + (p + 1));

                    float y = top - 10;
                    List<String> body = pages.get(p);
                    write(cs, bold, 14, MARGIN, y, body.getFirst());
                    y -= LEADING * 2;
                    for (String line : body.subList(1, body.size())) {
                        if (line.startsWith("    ")) {
                            write(cs, mono, 9.5f, MARGIN + 10, y, line.stripTrailing());
                            y -= LEADING - 2;
                            continue;
                        }
                        if (line.isBlank()) {
                            y -= LEADING / 2;
                            continue;
                        }
                        for (String wrapped : wrap(line)) {
                            write(cs, regular, FONT_SIZE, MARGIN, y, wrapped);
                            y -= LEADING;
                        }
                    }
                    if (y < MARGIN) {
                        throw new IllegalStateException("Page " + (p + 1) + " of " + in + " overflows, split it");
                    }
                }
            }
            pdf.getDocumentInformation().setTitle(docTitle);
            pdf.getDocumentInformation().setAuthor("DevDocs RAG Assistant sample docs");
            pdf.save(out.toFile());
        }
        System.out.println(out + ": " + pages.size() + " pages");
    }

    private static void write(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static List<String> wrap(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : line.split(" ")) {
            if (current.length() + word.length() + 1 > WRAP_CHARS && !current.isEmpty()) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }
}
