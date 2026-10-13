package com.chien.devdocs.document;

import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.AiServiceUnavailableException;
import com.chien.devdocs.common.exception.DuplicateDocumentException;
import com.chien.devdocs.common.exception.FileTooLargeException;
import com.chien.devdocs.common.exception.InvalidFileException;
import com.chien.devdocs.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

class IngestionServiceTest {

    private static final String MARKDOWN = """
            # Spring Transactions

            @Transactional mặc định dùng propagation REQUIRED: tham gia transaction hiện có, nếu chưa có thì tạo mới.

            ## REQUIRES_NEW

            REQUIRES_NEW luôn tạo transaction mới và tạm dừng transaction hiện tại cho tới khi transaction mới kết thúc.
            """;

    @TempDir
    Path storageDir;

    private SourceDocumentRepository repository;
    private VectorStore vectorStore;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        repository = mock(SourceDocumentRepository.class);
        vectorStore = mock(VectorStore.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByChecksum(any())).thenReturn(Optional.empty());

        RagProperties props = new RagProperties(5, 0.5, 3000, 1000, 500, 0, 20, 5, 30,
                DataSize.ofMegabytes(20), storageDir.toString());
        service = newService(props);
    }

    private IngestionService newService(RagProperties props) {
        FileStorage storage = new FileStorage(props);
        var pipeline = new ChunkingPipeline(new DocumentLoader(), new TextCleaner(), new ChunkSplitter(props),
                storage, props);
        return new IngestionService(repository, vectorStore, pipeline, storage, props);
    }

    private static MockMultipartFile markdown(String name, String content) {
        return new MockMultipartFile("file", name, "text/markdown", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexesMarkdownWithAllRequiredMetadata() {
        SourceDocument doc = service.upload(markdown("02_Spring.md", MARKDOWN), Topic.SPRING);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(doc.getPageCount()).isEqualTo(2);
        assertThat(doc.getChunkCount()).isPositive();
        assertThat(doc.getIndexedAt()).isNotNull();

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> chunks = captor.getValue();
        assertThat(chunks).hasSize(doc.getChunkCount());
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getMetadata())
                    .containsEntry(ChunkMetadata.DOCUMENT_ID, doc.getId().toString())
                    .containsEntry(ChunkMetadata.FILE_NAME, "02_Spring.md")
                    .containsEntry(ChunkMetadata.TOPIC, "SPRING")
                    .containsEntry(ChunkMetadata.CHUNK_INDEX, i)
                    .containsKey(ChunkMetadata.PAGE_NUMBER);
        }
        assertThat(chunks).extracting(c -> c.getMetadata().get(ChunkMetadata.PAGE_NUMBER)).contains(1, 2);
    }

    @Test
    void deletesOldChunksBeforeAddingNewOnesSoReindexDoesNotDuplicate() {
        service.upload(markdown("a.md", MARKDOWN), Topic.SPRING);

        var order = inOrder(vectorStore);
        order.verify(vectorStore).delete(any(Filter.Expression.class));
        order.verify(vectorStore).add(anyList());
    }

    @Test
    void rejectsUnsupportedExtension() {
        var docx = new MockMultipartFile("file", "cv.docx", "application/octet-stream", new byte[]{1, 2});
        assertThatThrownBy(() -> service.upload(docx, Topic.OTHER))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining(".pdf và .md");
    }

    @Test
    void rejectsContentTypeNotMatchingExtension() {
        var fakePdf = new MockMultipartFile("file", "a.pdf", "text/plain", "%PDF-1.7".getBytes());
        assertThatThrownBy(() -> service.upload(fakePdf, Topic.OTHER)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void rejectsPdfWithoutPdfSignature() {
        var fakePdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "not a pdf".getBytes());
        assertThatThrownBy(() -> service.upload(fakePdf, Topic.OTHER))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void rejectsFileLargerThanLimit() {
        RagProperties small = new RagProperties(5, 0.5, 3000, 1000, 500, 0, 20, 5, 30,
                DataSize.ofBytes(10), storageDir.toString());
        var svc = newService(small);

        assertThatThrownBy(() -> svc.upload(markdown("big.md", MARKDOWN), Topic.SPRING))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void duplicateOfIndexedDocumentIsRejectedWithoutEmbedding() {
        SourceDocument existing = new SourceDocument(java.util.UUID.randomUUID(), "old.md", "text/markdown",
                10, "x", Topic.SPRING, "p");
        existing.markIndexed(1, 1);
        when(repository.findByChecksum(any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.upload(markdown("again.md", MARKDOWN), Topic.SPRING))
                .isInstanceOf(DuplicateDocumentException.class)
                .satisfies(e -> assertThat(((DuplicateDocumentException) e).getBody().getProperties())
                        .containsEntry("existingDocumentId", existing.getId()));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void duplicateUploadOfPendingDocumentDoesNotStartAnotherIndexingRun() {
        SourceDocument pending = new SourceDocument(java.util.UUID.randomUUID(), "pending.pdf", "application/pdf",
                10, "x", Topic.OTHER, "p");
        when(repository.findByChecksum(any())).thenReturn(Optional.of(pending));
        var pdf = new MockMultipartFile("file", "copy.pdf", "application/pdf", "%PDF-1.4 body".getBytes());

        assertThatThrownBy(() -> service.upload(pdf, Topic.OTHER)).isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void duplicateOfDocumentStillBeingProcessedIsRejectedWithoutIndexingTwice() {
        SourceDocument processing = new SourceDocument(java.util.UUID.randomUUID(), "old.md", "text/markdown",
                10, "x", Topic.SPRING, "p");
        processing.markProcessing();
        when(repository.findByChecksum(any())).thenReturn(Optional.of(processing));

        assertThatThrownBy(() -> service.upload(markdown("again.md", MARKDOWN), Topic.SPRING))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("đang được xử lý")
                .satisfies(e -> assertThat(((DuplicateDocumentException) e).getBody().getProperties())
                        .containsEntry("existingDocumentId", processing.getId()));
        verify(repository, never()).save(any());
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void reindexOfProcessingDocumentDoesNotDeleteItsChunks() {
        SourceDocument processing = new SourceDocument(java.util.UUID.randomUUID(), "pending.md", "text/markdown",
                10, "x", Topic.OTHER, "p");
        processing.markProcessing();
        when(repository.findById(processing.getId())).thenReturn(Optional.of(processing));

        assertThatThrownBy(() -> service.reindex(processing.getId()))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("đang được xử lý");
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void concurrentUploadLosingTheUniqueChecksumRaceIs409AndLeavesNoOrphanFile() throws Exception {
        SourceDocument winner = new SourceDocument(java.util.UUID.randomUUID(), "a.md", "text/markdown",
                10, "x", Topic.SPRING, "p");
        winner.markProcessing();
        // Bước [2] chưa thấy bản ghi nào; tới lúc save thì request kia đã insert trước.
        when(repository.findByChecksum(any())).thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("uq_documents_checksum"));

        assertThatThrownBy(() -> service.upload(markdown("a.md", MARKDOWN), Topic.SPRING))
                .isInstanceOf(DuplicateDocumentException.class)
                .satisfies(e -> assertThat(((DuplicateDocumentException) e).getBody().getProperties())
                        .containsEntry("existingDocumentId", winner.getId()));
        try (var files = java.nio.file.Files.list(storageDir)) {
            assertThat(files).isEmpty();
        }
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void duplicateOfFailedDocumentIsReprocessedReusingTheSameRecord() {
        SourceDocument failed = new SourceDocument(java.util.UUID.randomUUID(), "old.md", "text/markdown",
                10, "x", Topic.OTHER, "p");
        failed.markFailed("boom");
        when(repository.findByChecksum(any())).thenReturn(Optional.of(failed));

        SourceDocument doc = service.upload(markdown("retry.md", MARKDOWN), Topic.SPRING);

        assertThat(doc.getId()).isEqualTo(failed.getId());
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(doc.getFileName()).isEqualTo("retry.md");
        assertThat(doc.getTopic()).isEqualTo(Topic.SPRING);
    }

    @Test
    void documentWithoutTextIsMarkedFailedWithExplanation() {
        SourceDocument doc = service.upload(markdown("empty.md", "#\n\n   \n"), Topic.OTHER);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).isEqualTo(IngestionService.NO_TEXT_MESSAGE);
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void blankPdfIsNotMarkedIndexed() throws Exception {
        byte[] bytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage()); pdf.save(output); bytes = output.toByteArray();
        }
        SourceDocument doc = service.upload(new MockMultipartFile("file", "blank.pdf", "application/pdf", bytes), Topic.OTHER);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).contains("Không trích xuất được văn bản");
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void encryptedPdfGetsActionableFailure() throws Exception {
        byte[] bytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.protect(new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission()));
            pdf.save(output); bytes = output.toByteArray();
        }
        SourceDocument doc = service.upload(new MockMultipartFile("file", "locked.pdf", "application/pdf", bytes), Topic.OTHER);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).contains("mật khẩu");
        assertThat(doc.getErrorMessage()).doesNotContain("owner-secret");
    }

    @Test
    void corruptedPdfGetsActionableFailure() {
        SourceDocument doc = service.upload(new MockMultipartFile("file", "broken.pdf", "application/pdf",
                "%PDF-1.4\nnot a PDF structure".getBytes(StandardCharsets.US_ASCII)), Topic.OTHER);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).contains("PDF").doesNotContain("Exception");
    }

    @Test
    void embeddingConnectionFailureMarksFailedAndSurfacesAs503() {
        doThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")))
                .when(vectorStore).add(anyList());

        assertThatThrownBy(() -> service.upload(markdown("a.md", MARKDOWN), Topic.SPRING))
                .isInstanceOf(AiServiceUnavailableException.class);

        ArgumentCaptor<SourceDocument> saved = ArgumentCaptor.forClass(SourceDocument.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DocumentStatus.FAILED);
        // Lỗi do Ollama, không phải do file → thông báo không được bảo người dùng kiểm tra lại file.
        assertThat(saved.getValue().getErrorMessage()).isEqualTo(IngestionService.AI_UNAVAILABLE_MESSAGE)
                .doesNotContain("I/O error", "Connection refused");
    }

    @Test
    void failureAfterThePdfWasReadDoesNotBlameTheFile() throws Exception {
        byte[] bytes;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Spring transactions use REQUIRED propagation by default for every method call.");
                content.endText();
            }
            pdf.save(output); bytes = output.toByteArray();
        }
        doThrow(new IllegalStateException("relation vector_store does not exist")).when(vectorStore).add(anyList());

        SourceDocument doc = service.upload(new MockMultipartFile("file", "ok.pdf", "application/pdf", bytes), Topic.OTHER);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).isEqualTo(IngestionService.INDEXING_FAILED_MESSAGE)
                .doesNotContain("vector_store");
    }

    @Test
    void documentsLeftPendingOrProcessingByAPreviousRunAreMarkedFailedAtStartup() {
        SourceDocument pending = new SourceDocument(java.util.UUID.randomUUID(), "a.pdf", "application/pdf",
                10, "a", Topic.OTHER, "a");
        SourceDocument processing = new SourceDocument(java.util.UUID.randomUUID(), "b.md", "text/markdown",
                10, "b", Topic.OTHER, "b");
        processing.markProcessing();
        when(repository.search(null, DocumentStatus.PENDING)).thenReturn(List.of(pending));
        when(repository.search(null, DocumentStatus.PROCESSING)).thenReturn(List.of(processing));

        service.failInterruptedDocuments();

        assertThat(List.of(pending, processing)).allSatisfy(doc -> {
            assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
            assertThat(doc.getErrorMessage()).isEqualTo(IngestionService.INTERRUPTED_MESSAGE);
        });
        verify(repository).save(pending);
        verify(repository).save(processing);
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void deleteRemovesChunksThenRecordThenFile() {
        SourceDocument doc = service.upload(markdown("a.md", MARKDOWN), Topic.SPRING);
        when(repository.findById(doc.getId())).thenReturn(Optional.of(doc));

        service.delete(doc.getId());

        var order = inOrder(vectorStore, repository);
        order.verify(vectorStore).delete(any(Filter.Expression.class));
        order.verify(repository).delete(doc);
        assertThat(doc.getStoragePath()).doesNotContain("/", "\\");   // tương đối so với storage-dir
        assertThat(storageDir.resolve(doc.getStoragePath())).doesNotExist();
    }

    @Test
    void checksumIsSha256Hex() {
        assertThat(IngestionService.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
