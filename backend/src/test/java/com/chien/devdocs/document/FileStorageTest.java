package com.chien.devdocs.document;

import com.chien.devdocs.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageTest {

    @TempDir
    Path root;

    private FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorage(new RagProperties(5, 0.5, 3000, 150, 60, 200, 10, 30,
                DataSize.ofMegabytes(20), root.toString()));
    }

    @Test
    void savesUnderStorageDirAndReturnsRelativeFileName() throws IOException {
        UUID id = UUID.randomUUID();

        String stored = storage.save(id, FileType.MARKDOWN, "# x".getBytes());

        assertThat(stored).isEqualTo(id + ".md");
        assertThat(storage.resolve(stored)).isEqualTo(root.resolve(stored).toAbsolutePath().normalize());
        assertThat(Files.readString(storage.resolve(stored))).isEqualTo("# x");
    }

    @Test
    void legacyAbsolutePathIsUsedAsIsWhileTheFileStillExists() throws IOException {
        Path elsewhere = Files.createTempFile("legacy", ".pdf");
        try {
            assertThat(storage.resolve(elsewhere.toString())).isEqualTo(elsewhere);
        } finally {
            Files.deleteIfExists(elsewhere);
        }
    }

    @Test
    void legacyAbsolutePathFromAnotherMachineFallsBackToFileNameInStorageDir() {
        String stored = storage.save(UUID.randomUUID(), FileType.PDF, "%PDF-1.7".getBytes());
        String oldAbsolute = Path.of("/old/machine/storage", stored).toAbsolutePath().toString();

        assertThat(storage.resolve(oldAbsolute)).exists().isEqualTo(root.resolve(stored).toAbsolutePath().normalize());
    }

    @Test
    void relativePathEscapingStorageDirIsRejected() {
        assertThatThrownBy(() -> storage.resolve("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesTheFileAndIgnoresInvalidPaths() {
        String stored = storage.save(UUID.randomUUID(), FileType.MARKDOWN, "# x".getBytes());

        storage.delete(stored);
        storage.delete("../outside.md");   // chỉ log cảnh báo, không ném lỗi

        assertThat(root.resolve(stored)).doesNotExist();
    }
}
