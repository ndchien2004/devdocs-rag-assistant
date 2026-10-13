package com.chien.devdocs.document;

import com.chien.devdocs.common.Topic;
import com.chien.devdocs.document.dto.DocumentResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final IngestionService ingestionService;

    public DocumentController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** Phase 1–3: xử lý đồng bộ — request chờ tới khi index xong. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("topic") Topic topic) {
        DocumentResponse body = DocumentResponse.from(ingestionService.upload(file, topic));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    public List<DocumentResponse> list(@RequestParam(required = false) Topic topic,
                                       @RequestParam(required = false) DocumentStatus status) {
        return ingestionService.list(topic, status).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(ingestionService.get(id));
    }

    @GetMapping(value = "/{id}/content", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> content(@PathVariable UUID id) throws IOException {
        var document = ingestionService.get(id);
        if (!"application/pdf".equalsIgnoreCase(document.getContentType())) {
            return ResponseEntity.notFound().build();
        }
        var file = new FileSystemResource(ingestionService.filePath(document));
        if (!file.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(Files.size(file.getFile().toPath()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline().filename(document.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        ingestionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reindex")
    public DocumentResponse reindex(@PathVariable UUID id) {
        return DocumentResponse.from(ingestionService.reindex(id));
    }
}
