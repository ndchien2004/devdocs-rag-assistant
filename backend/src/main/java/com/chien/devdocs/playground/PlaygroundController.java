package com.chien.devdocs.playground;

import com.chien.devdocs.playground.dto.EmbeddingResponse;
import com.chien.devdocs.playground.dto.SimilarityResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/playground")
public class PlaygroundController {

    private final PlaygroundService playgroundService;

    public PlaygroundController(PlaygroundService playgroundService) {
        this.playgroundService = playgroundService;
    }

    @GetMapping("/hello")
    public Map<String, String> hello(@RequestParam @NotBlank @Size(max = 1000) String q) {
        return Map.of("question", q, "answer", playgroundService.hello(q));
    }

    @GetMapping("/embed")
    public EmbeddingResponse embed(@RequestParam @NotBlank @Size(max = 2000) String text) {
        return playgroundService.embed(text);
    }

    @GetMapping("/similarity")
    public SimilarityResponse similarity(@RequestParam @NotBlank @Size(max = 2000) String a,
                                         @RequestParam @NotBlank @Size(max = 2000) String b) {
        return playgroundService.similarity(a, b);
    }
}
