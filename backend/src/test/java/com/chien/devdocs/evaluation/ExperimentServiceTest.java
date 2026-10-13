package com.chien.devdocs.evaluation;

import com.chien.devdocs.document.ChunkSplitter;
import com.chien.devdocs.document.ChunkingPipeline;
import com.chien.devdocs.document.SourceDocumentRepository;
import com.chien.devdocs.evaluation.dto.EvaluationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExperimentServiceTest {

    private JdbcTemplate jdbc;
    private ExperimentService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new ExperimentService(jdbc, mock(EmbeddingModel.class), mock(SourceDocumentRepository.class),
                mock(ChunkingPipeline.class), mock(EvaluationService.class), 1024);
    }

    @Test
    void dropsOnlyTablesMatchingTheExperimentNamingPattern() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(
                "exp_token_500_o0", "exp_window_150_o60", "exp_notes", "exp_window_150_o60; drop table documents"));

        List<String> dropped = service.dropExperimentTables();

        assertThat(dropped).containsExactly("exp_token_500_o0", "exp_window_150_o60");
        verify(jdbc).execute("DROP TABLE IF EXISTS \"exp_token_500_o0\"");
        verify(jdbc).execute("DROP TABLE IF EXISTS \"exp_window_150_o60\"");
        verify(jdbc, never()).execute("DROP TABLE IF EXISTS \"exp_notes\"");
    }

    @Test
    void nothingToDropReturnsEmptyList() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        assertThat(service.dropExperimentTables()).isEmpty();
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void rejectsInvalidChunkingConfigBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.runChunking(150, 150, ChunkSplitter.Strategy.WINDOW,
                EvaluationRequest.defaults())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.runChunking(10, 0, ChunkSplitter.Strategy.TOKEN,
                EvaluationRequest.defaults())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
