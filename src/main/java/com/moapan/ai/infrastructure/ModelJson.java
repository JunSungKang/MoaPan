package com.moapan.ai.infrastructure;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Untrusted model output is data, never an instruction or a tool invocation. */
public final class ModelJson {
    private ModelJson() {}
    private static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
            .maxDocumentLength(32768).maxStringLength(24000).maxNameLength(128)
            .maxNumberLength(64).maxTokenCount(8192).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public static JsonNode object(String text,int maximum) {
        if(text==null||text.length()>maximum)throw new IllegalArgumentException("model_invalid_json");
        try {var root=JSON.readTree(text);if(!root.isObject())throw new IllegalArgumentException();return root;}
        catch(Exception e){throw new IllegalArgumentException("model_invalid_json");}
    }
    public static String content(byte[] bytes,int maximum) {
        if(bytes==null||bytes.length>maximum)throw new IllegalArgumentException("model_invalid_reply");
        try {
            var root=JSON.readTree(bytes);var choices=root.path("choices");
            if(!root.isObject()||!choices.isArray()||choices.size()!=1)throw new IllegalArgumentException();
            var choice=choices.path(0);var message=choice.path("message");
            if(!"stop".equals(choice.path("finish_reason").asText())||!message.isObject()
                ||!message.path("content").isString()
                ||(message.has("role")&&!"assistant".equals(message.path("role").asText())))throw new IllegalArgumentException();
            for(String key:new String[]{"function_call","refusal"})if(message.hasNonNull(key))throw new IllegalArgumentException();
            var tools=message.path("tool_calls");
            if(!tools.isMissingNode()&&!tools.isNull()&&(!tools.isArray()||!tools.isEmpty()))throw new IllegalArgumentException();
            String text=message.path("content").asText().strip();if(text.isBlank())throw new IllegalArgumentException();return text;
        }catch(Exception e){throw new IllegalArgumentException("model_invalid_reply");}
    }
}
