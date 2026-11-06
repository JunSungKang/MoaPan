package com.moapan;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import com.moapan.translation.infrastructure.LocalTranslator;
import com.moapan.classification.infrastructure.LocalClassifier;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureAiCompatibilityTest {
    @TempDir Path work;
    @Test void deploymentRenamePreservesEveryOriginalExecutablePolicyByte() throws Exception {
        var baseline=tools.jackson.databind.json.JsonMapper.builder().build().readTree(Files.readString(Path.of("src/test/resources/infrastructure-ai-policy-baseline.json")));
        int checked=0;
        for(var entry:baseline.path("classes").properties()){
            try(var stream=getClass().getClassLoader().getResourceAsStream(entry.getKey())){
                assertNotNull(stream,entry.getKey());
                assertEquals(entry.getValue().asText(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())),entry.getKey()+": policy change requires explicit data handling, not a deployment rename");
                checked++;
            }
        }
        assertTrue(checked>=9);
        assertEquals(baseline.path("translationPolicy").asText(),com.moapan.translation.application.TranslationWorker.POLICY);
        assertEquals(baseline.path("translationProvider").asText(),com.moapan.translation.application.TranslationWorker.PROVIDER);
        assertEquals(baseline.path("classificationPolicy").asText(),LocalClassifier.POLICY);
    }
    @Test void canonicalDeploymentSettingsEnableByteIdenticalLegacyAdapters() throws Exception {
        Path key=work.resolve("synthetic-model.key");Files.writeString(key,"a".repeat(64));
        try(var context=new AnnotationConfigApplicationContext()){
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("canonical",java.util.Map.of("DAMGOX2_TRANSLATION_URL","http://translator:11435/v1/chat/completions","DAMGOX2_TRANSLATION_KEY_FILE",key.toString(),"DAMGOX2_CLASSIFICATION_URL","http://classifier:11436/v1/chat/completions","DAMGOX2_CLASSIFICATION_KEY_FILE",key.toString())));
            context.getEnvironment().getPropertySources().addLast(new PropertiesPropertySource("application",PropertiesLoaderUtils.loadAllProperties("application.properties")));
            context.registerBean(PropertySourcesPlaceholderConfigurer.class);
            context.register(LocalTranslator.class,LocalClassifier.class);context.refresh();
            assertTrue(context.getBean(LocalTranslator.class).enabled());assertTrue(context.getBean(LocalClassifier.class).enabled());
        }
    }
}
