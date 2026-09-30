package com.chien.devdocs.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc file thành danh sách "trang" (bước [5] của luồng ingestion).
 * <ul>
 *   <li>PDF: 1 {@link Document} / trang, metadata {@code page_number} do {@link PagePdfDocumentReader} ghi sẵn.</li>
 *   <li>Markdown: 1 {@link Document} / mục (tách theo heading). Markdown không có "trang", nên
 *       {@code page_number} = số thứ tự mục (1, 2, 3...) và {@code section_title} = tiêu đề mục.</li>
 * </ul>
 */
@Component
public class DocumentLoader {

    public static final String PAGE_NUMBER = "page_number";
    public static final String SECTION_TITLE = "section_title";

    public record LoadedFile(List<Document> pages, int pageCount) {
    }

    public LoadedFile load(Path file, FileType type) {
        return switch (type) {
            case PDF -> loadPdf(file);
            case MARKDOWN -> loadMarkdown(file);
        };
    }

    private LoadedFile loadPdf(Path file) {
        var config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .build();
        List<Document> pages = new PagePdfDocumentReader(new FileSystemResource(file), config).get();
        return new LoadedFile(pages, countPdfPages(file));
    }

    private static int countPdfPages(Path file) {
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            return pdf.getNumberOfPages();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file PDF", e);
        }
    }

    private LoadedFile loadMarkdown(Path file) {
        var config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(false)
                .withIncludeCodeBlock(true)      // code block nằm chung với đoạn văn của mục đó
                .withIncludeBlockquote(true)
                .build();
        List<Document> raw = new MarkdownDocumentReader(new FileSystemResource(file), config).get();

        // MarkdownDocumentReader tách mỗi heading thành 1 Document, metadata "title" = nội dung heading.
        // Gộp các Document liên tiếp có cùng title thành 1 mục, đánh số mục từ 1.
        List<Document> sections = new ArrayList<>();
        String currentTitle = null;
        StringBuilder buffer = new StringBuilder();
        for (Document d : raw) {
            String title = (String) d.getMetadata().get("title");
            if (title != null && !title.equals(currentTitle)) {
                flushSection(sections, currentTitle, buffer);
                currentTitle = title;
            }
            if (!buffer.isEmpty()) {
                buffer.append("\n\n");
            }
            buffer.append(d.getText());
        }
        flushSection(sections, currentTitle, buffer);
        return new LoadedFile(sections, sections.size());
    }

    private static void flushSection(List<Document> sections, String title, StringBuilder buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(PAGE_NUMBER, sections.size() + 1);
        if (title != null) {
            metadata.put(SECTION_TITLE, title);
        }
        String text = title != null && !buffer.toString().startsWith(title)
                ? title + "\n\n" + buffer
                : buffer.toString();
        sections.add(new Document(text, metadata));
        buffer.setLength(0);
    }
}
