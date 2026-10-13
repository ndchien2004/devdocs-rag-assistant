package com.chien.devdocs.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    @Test
    void defaultsMatchTheConfigurationChosenInPhase4() {
        RagProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("app.rag", RagProperties.class);

        assertThat(defaults.chunkSize()).isEqualTo(150);
        assertThat(defaults.chunkOverlap()).isEqualTo(60);
        assertThat(defaults.topK()).isEqualTo(5);
        assertThat(defaults.similarityThreshold()).isEqualTo(0.5);
    }

    @Test
    void applicationYmlAgreesWithCodeDefaults() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        List<PropertySource<?>> yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        yml.forEach(env.getPropertySources()::addLast);
        RagProperties fromYml = Binder.get(env).bind("app.rag", RagProperties.class).get();
        RagProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("app.rag", RagProperties.class);

        assertThat(fromYml.chunkSize()).isEqualTo(defaults.chunkSize());
        assertThat(fromYml.chunkOverlap()).isEqualTo(defaults.chunkOverlap());
        assertThat(fromYml.topK()).isEqualTo(defaults.topK());
        assertThat(fromYml.similarityThreshold()).isEqualTo(defaults.similarityThreshold());
    }
}
