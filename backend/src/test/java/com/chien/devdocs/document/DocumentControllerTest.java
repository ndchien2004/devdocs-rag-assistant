package com.chien.devdocs.document;

import com.chien.devdocs.common.Topic;
import com.chien.devdocs.common.exception.DocumentNotFoundException;
import com.chien.devdocs.common.exception.DuplicateDocumentException;
import com.chien.devdocs.common.exception.InvalidFileException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    IngestionService ingestionService;

    private final MockMultipartFile file =
            new MockMultipartFile("file", "a.md", "text/markdown", "# Hi".getBytes());

    @Test
    void uploadReturns201WithLocation() throws Exception {
        SourceDocument doc = new SourceDocument(UUID.randomUUID(), "a.md", "text/markdown", 4, "c", Topic.JAVA, "p");
        doc.markIndexed(1, 1);
        when(ingestionService.upload(any(), eq(Topic.JAVA))).thenReturn(doc);

        mvc.perform(multipart("/api/v1/documents").file(file).param("topic", "JAVA"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/documents/" + doc.getId()))
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.chunkCount").value(1));
    }

    @Test
    void invalidFileIs400ProblemDetail() throws Exception {
        when(ingestionService.upload(any(), any())).thenThrow(new InvalidFileException("Định dạng không được hỗ trợ."));

        mvc.perform(multipart("/api/v1/documents").file(file).param("topic", "JAVA"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid file"))
                .andExpect(jsonPath("$.detail").value("Định dạng không được hỗ trợ."));
    }

    @Test
    void unknownTopicIs400() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file).param("topic", "PYTHON"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingFileIs400() throws Exception {
        mvc.perform(multipart("/api/v1/documents").param("topic", "JAVA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateIs409WithExistingId() throws Exception {
        UUID existing = UUID.randomUUID();
        when(ingestionService.upload(any(), any())).thenThrow(new DuplicateDocumentException(existing));

        mvc.perform(multipart("/api/v1/documents").file(file).param("topic", "JAVA"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate document"))
                .andExpect(jsonPath("$.existingDocumentId").value(existing.toString()))
                .andExpect(jsonPath("$.instance").value("/api/v1/documents"));
    }

    @Test
    void unknownDocumentIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(ingestionService.get(id)).thenThrow(new DocumentNotFoundException(id));

        mvc.perform(get("/api/v1/documents/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/documents/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DocumentNotFoundException(id)).when(ingestionService).delete(id);

        mvc.perform(delete("/api/v1/documents/{id}", id)).andExpect(status().isNotFound());
    }
}
