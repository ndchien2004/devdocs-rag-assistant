package com.chien.devdocs.evaluation;

import com.chien.devdocs.chat.RagMode;
import com.chien.devdocs.evaluation.dto.AnswerEvaluationReport;
import com.chien.devdocs.evaluation.dto.ChunkingExperimentRequest;
import com.chien.devdocs.evaluation.dto.EvaluationReport;
import com.chien.devdocs.evaluation.dto.EvaluationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final ExperimentService experimentService;
    private final AnswerEvaluationService answerEvaluationService;

    public EvaluationController(EvaluationService evaluationService, ExperimentService experimentService,
                                AnswerEvaluationService answerEvaluationService) {
        this.evaluationService = evaluationService;
        this.experimentService = experimentService;
        this.answerEvaluationService = answerEvaluationService;
    }

    /** Eval retrieval. Body tùy chọn: {"topK": 5, "similarityThreshold": 0.5, "useTopicFilter": false}. */
    @PostMapping("/run")
    public EvaluationReport run(@Valid @RequestBody(required = false) EvaluationRequest request) {
        return evaluationService.run(request != null ? request : EvaluationRequest.defaults());
    }

    /** E1 / E4: index lại mọi tài liệu với chunk size / overlap khác vào bảng riêng rồi chạy eval retrieval. */
    @PostMapping("/experiments/chunking")
    public EvaluationReport chunking(@Valid @RequestBody ChunkingExperimentRequest request) {
        return experimentService.runChunking(request.chunkSize(), request.chunkOverlapOrZero(),
                request.strategyOrDefault(), request.toEvaluation());
    }

    /** E5: eval ở mức câu trả lời (gọi LLM thật) cho một RagMode. */
    @PostMapping("/answers")
    public AnswerEvaluationReport answers(@RequestParam(defaultValue = "MANUAL") RagMode mode,
                                          @RequestParam(required = false) @Min(1) @Max(10) Integer topK) {
        return answerEvaluationService.run(mode, topK);
    }
}
