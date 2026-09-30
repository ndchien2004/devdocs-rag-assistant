package com.chien.devdocs.document;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Định dạng được hỗ trợ (BR-ING-01) và các Content-Type chấp nhận cho từng loại.
 */
public enum FileType {

    PDF(".pdf", "application/pdf", Set.of("application/pdf")),
    // Trình duyệt / curl thường gửi .md với text/plain hoặc application/octet-stream.
    MARKDOWN(".md", "text/markdown",
            Set.of("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream"));

    private final String extension;
    private final String canonicalContentType;
    private final Set<String> acceptedContentTypes;

    FileType(String extension, String canonicalContentType, Set<String> acceptedContentTypes) {
        this.extension = extension;
        this.canonicalContentType = canonicalContentType;
        this.acceptedContentTypes = acceptedContentTypes;
    }

    public static Optional<FileType> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (FileType type : values()) {
            if (lower.endsWith(type.extension)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public boolean accepts(String contentType) {
        if (contentType == null) {
            return false;
        }
        String base = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return acceptedContentTypes.contains(base);
    }

    public String extension() {
        return extension;
    }

    public String canonicalContentType() {
        return canonicalContentType;
    }
}
