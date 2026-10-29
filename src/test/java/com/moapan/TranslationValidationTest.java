package com.moapan;

import com.moapan.article.domain.ContentProcessor;
import com.moapan.article.application.EditorialService;
import com.moapan.article.api.EditorialController;
import com.moapan.edition.application.NewsService;
import com.moapan.edition.application.NewsSearch;
import com.moapan.edition.application.RepresentativeInterests;
import com.moapan.edition.api.NewsController;
import com.moapan.translation.domain.TranslationValidator;
import com.moapan.translation.domain.TranslationGlossary;
import com.moapan.translation.domain.TranslationFailure;
import com.moapan.translation.infrastructure.LocalTranslator;
import com.moapan.translation.infrastructure.ArticleTranslations;
import com.moapan.translation.api.TranslationStatusController;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationValidationTest {
    @TestFactory List<DynamicTest> fixedEvaluationCorpus() throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/translation/validation.tsv"),StandardCharsets.UTF_8))){
            var rows=reader.lines().filter(s->!s.startsWith("#")&&!s.isBlank()).toList();assertTrue(rows.size()>=100);
            return rows.stream().map(line->{var f=line.split("\t",-1);return DynamicTest.dynamicTest(f[0],()->{
                if(f[4].equals("PASS"))assertDoesNotThrow(()->TranslationValidator.validate(f[2],f[3],f[1]));
                else {var failure=assertThrows(TranslationFailure.class,()->TranslationValidator.validate(f[2],f[3],f[1]));assertEquals(f[4],failure.getMessage());assertFalse(failure.infrastructure());}
            });}).toList();
        }
    }
    @Test void rejectsChangedUnitsNegationDirectionAndIdentifiers(){
        for(var pair:new String[][]{
            {"Memory is 100 MB.","메모리는 100GB입니다."},
            {"The score is at least 10.","점수는 10 이하입니다."},
            {"The error is ±3.","오차는 3입니다."},
            {"The rate is 100 Mbps.","속도는 100MB입니다."},
            {"The difference is 15%.","차이는 15%p입니다."},
            {"Latency decreased by 10%.","지연 시간이 10% 증가했습니다."},
            {"There is no evidence in 3 tests.","3개 시험에 증거가 있습니다."},
            {"Fix CVE-2026-12345.","CVE-2026-12346 수정."},
            {"Use version 2.4.1.","버전 2.4.2 사용."}})
            assertThrows(TranslationFailure.class,()->TranslationValidator.validate(pair[0],pair[1],"ko"));
    }
    @Test void protectedIdentifiersMustAppearExactlyOnce(){
        var p=TranslationValidator.protect("Fix CVE-2026-12345 in version 2.4.1 with 100 MB.");
        assertFalse(p.text().contains("CVE-2026"));
        assertEquals("Fix CVE-2026-12345 in version 2.4.1 with 100 MB.",p.restore(p.text()));
        assertThrows(TranslationFailure.class,()->p.restore(p.text().replace("__MP0__","")));
        assertThrows(TranslationFailure.class,()->p.restore(p.text()+" __MP0__"));
    }
    @Test void normalizesTimeAndMagnitudeButDoesNotSortAwayReversals(){
        assertEquals(TranslationValidator.facts("a minute"),TranslationValidator.facts("1분"));
        assertEquals(TranslationValidator.facts("1 million vectors"),TranslationValidator.facts("100만 벡터"));
        assertNotEquals(TranslationValidator.facts("120 ms to 90 ms"),TranslationValidator.facts("90ms에서 120ms"));
        assertNotEquals(TranslationValidator.facts("15%"),TranslationValidator.facts("15%p"));
    }
}
