package com.moapan;

import com.moapan.news.LocalTranslator;
import com.moapan.news.TranslationGlossary;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalTranslatorTest {
    @Test void glossaryDoesNotIntroduceAbsentConcepts(){
        assertFalse(LocalTranslator.prompt("검색 재현율은 높아졌다.","en").contains("retrieval-augmented generation"));
        assertTrue(LocalTranslator.prompt("검색 증강 생성은 외부 문서를 참고한다.","en").contains("retrieval-augmented generation"));
    }
    @Test void glossaryMatchesWholeTermsAndKoreanParticles(){
        assertEquals("",TranslationGlossary.matching("The authorizationless recallable feature.","ko"));
        assertTrue(TranslationGlossary.matching("SNAPSHOT ISOLATION preserves consistency.","ko").contains("snapshot isolation=스냅샷 격리"));
        assertTrue(TranslationGlossary.matching("처리량은 늘고 중앙값은 줄었다.","en").contains("처리량=throughput"));
        assertEquals("근사 최근접 이웃=approximate nearest neighbor",TranslationGlossary.matching("근사 최근접 이웃을 검색한다.","en"));
        assertFalse(TranslationGlossary.matching("The database performs well.","ko").contains("snapshot"));
    }
    @Test void rejectsExternalEndpointsAndMissingLocalSecret(){
        assertThrows(IllegalStateException.class,()->new LocalTranslator("https://example.com/v1/chat/completions",""));
        assertThrows(IllegalStateException.class,()->new LocalTranslator("http://127.0.0.1:11435/v1/chat/completions",""));
        assertFalse(new LocalTranslator("","").enabled());
    }
    @Test void refusesChangedNumbersIdentifiersAndIncompleteReasoning(){
        LocalTranslator.validate("PostgreSQL 18 improves latency by 32%.","PostgreSQL 18은 지연 시간을 32% 개선합니다.","ko");
        assertThrows(IllegalStateException.class,()->LocalTranslator.validate("Latency falls 32%.","지연 시간이 42% 감소합니다.","ko"));
        assertThrows(IllegalStateException.class,()->LocalTranslator.validate("CVE-2026-12345","취약점 2026 12345","ko"));
        assertThrows(IllegalStateException.class,()->LocalTranslator.validate("Database","Database","ko"));
        assertThrows(IllegalStateException.class,()->LocalTranslator.validate("Database","<think>데이터베이스","ko"));
    }
}
