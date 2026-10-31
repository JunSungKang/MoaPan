package com.moapan.ai.infrastructure;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Same server/template/tokenizer as inference; no inference, redirects, or external input URL. */
public final class ModelTokenBudget {
    private ModelTokenBudget() {}
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public static void check(HttpClient http,URI endpoint,String key,Map<String,Object> body,int outputTokens)throws Exception {
        if(outputTokens<1||outputTokens>1200)throw new IllegalArgumentException("ai_output_budget_invalid");
        var template=post(http,endpoint.resolve("/apply-template"),key,Map.of("messages",body.get("messages"),"chat_template_kwargs",Map.of("enable_thinking",false)),65536);
        var prompt=template.path("prompt");if(!prompt.isString()||prompt.asText().length()>24000)throw new IllegalArgumentException("ai_template_invalid");
        var tokenized=post(http,endpoint.resolve("/tokenize"),key,Map.of("content",prompt.asText(),"add_special",true,"parse_special",true,"with_pieces",false),65536);
        var tokens=tokenized.path("tokens");if(!tokens.isArray()||tokens.size()==0||tokens.size()>8192)throw new IllegalArgumentException("ai_tokenizer_invalid");
        for(var token:tokens)if(!token.isIntegralNumber()||token.asLong()<0)throw new IllegalArgumentException("ai_tokenizer_invalid");
        if(tokens.size()+outputTokens+64>4096)throw new ArticleBudgetExceeded();
    }
    private static tools.jackson.databind.JsonNode post(HttpClient http,URI endpoint,String key,Map<String,Object> body,int limit)throws Exception {
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Authorization","Bearer "+key).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(),limit));
        if(response.statusCode()!=200)throw new IllegalStateException("ai_tokenizer_unavailable");
        return ModelJson.object(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8),limit);
    }
    public static final class ArticleBudgetExceeded extends IllegalArgumentException {public ArticleBudgetExceeded(){super("ai_context_budget_exceeded");}}
}
