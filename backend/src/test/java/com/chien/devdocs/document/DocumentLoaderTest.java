package com.chien.devdocs.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentLoaderTest {

    private final DocumentLoader loader = new DocumentLoader();

    @TempDir
    Path tmp;

    @Test
    void markdownIsSplitByHeadingWithSectionNumbers() throws IOException {
        Path md = tmp.resolve("notes.md");
        Files.writeString(md, """
                # Java Core

                Giới thiệu chung.

                ## String pool

                String là immutable.

                ```java
                String a = "hi";
                ```

                ## HashMap

                HashMap dùng bảng băm.
                """);

        DocumentLoader.LoadedFile loaded = loader.load(md, FileType.MARKDOWN);

        List<Document> sections = loaded.pages();
        assertThat(loaded.pageCount()).isEqualTo(3);
        assertThat(sections).extracting(d -> d.getMetadata().get(DocumentLoader.PAGE_NUMBER))
                .containsExactly(1, 2, 3);
        assertThat(sections).extracting(d -> d.getMetadata().get(DocumentLoader.SECTION_TITLE))
                .containsExactly("Java Core", "String pool", "HashMap");
        assertThat(sections.get(1).getText())
                .contains("String pool")
                .contains("String là immutable.")
                .contains("String a = \"hi\";");
        assertThat(sections.get(2).getText()).contains("HashMap dùng bảng băm.");
    }

    @Test
    void pdfIsReadOnePagePerDocumentWithPageNumbers() throws IOException {
        Path pdf = tmp.resolve("guide.pdf");
        writePdf(pdf, List.of("First page about beans.", "Second page about transactions.", "Third page."));

        DocumentLoader.LoadedFile loaded = loader.load(pdf, FileType.PDF);

        assertThat(loaded.pageCount()).isEqualTo(3);
        assertThat(loaded.pages()).hasSize(3);
        assertThat(loaded.pages()).extracting(d -> d.getMetadata().get(DocumentLoader.PAGE_NUMBER))
                .containsExactly(1, 2, 3);
        // PDF reader giữ layout (nhiều khoảng trắng) — TextCleaner sẽ gộp lại ở bước sau.
        assertThat(loaded.pages().get(1).getText().replaceAll("\\s+", " "))
                .contains("Second page about transactions.");
    }

    static void writePdf(Path target, List<String> pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(target.toFile());
        }
    }
}
