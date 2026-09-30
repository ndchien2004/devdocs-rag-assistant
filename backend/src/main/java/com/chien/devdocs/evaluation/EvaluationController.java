package com.chien.devdocs.evaluation;

import com.chien.devdocs.evaluation.dto.EvaluationReport;
import com.chien.devdocs.evaluation.dto.EvaluationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** Body tùy chọn: {"topK": 5, "similarityThreshold": 0.5, "useTopicFilter": false}. */
    @PostMapping("/run")
    public EvaluationReport run(@Valid @RequestBody(required = false) EvaluationRequest request) {
        return evaluationService.run(request != null ? request : EvaluationRequest.defaults());
    }
}
