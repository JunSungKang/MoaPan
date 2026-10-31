package com.moapan.ai.infrastructure;

import java.io.*;
import java.net.http.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded SSE text only. Closing a cancelled stream propagates disconnect to llama.cpp. */
public final class ModelStream {
    private ModelStream() {}
    public static String request(HttpClient http,HttpRequest request,int byteLimit,int characterLimit,int seconds)throws Exception {
        Thread caller=Thread.currentThread();var expired=new AtomicBoolean();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body();var timer=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())){
            if(response.statusCode()!=200)throw new IOException("model_stream_unavailable");
            timer.schedule(()->{expired.set(true);try{body.close();}catch(IOException ignored){}caller.interrupt();},seconds,TimeUnit.SECONDS);
            try{return read(body,byteLimit,characterLimit);}finally{timer.shutdownNow();if(expired.get())Thread.interrupted();}
        }
    }
    public static final class Invalid extends IOException {public Invalid(String code){super(code);}}
    public static String read(InputStream body,int byteLimit,int characterLimit)throws IOException {
        StringBuilder content=new StringBuilder();var line=new ByteArrayOutputStream();int bytes=0,events=0;boolean finished=false,done=false;
        for(int value;(value=body.read())!=-1;){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("model_stream_interrupted");
            if(++bytes>byteLimit)throw new Invalid("model_stream_oversized");
            if(value!='\n'){if(line.size()>=8192)throw new Invalid("model_stream_line_oversized");line.write(value);continue;}
            String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(line.toByteArray())).toString().strip();}catch(CharacterCodingException e){throw new Invalid("model_stream_encoding");}line.reset();
            if(text.isEmpty()||text.startsWith(":"))continue;
            if(!text.startsWith("data: ")||++events>4096||done)throw new Invalid("model_stream_invalid");
            String data=text.substring(6);if(data.equals("[DONE]")){if(!finished)throw new Invalid("model_stream_unfinished");done=true;continue;}
            if(finished)throw new Invalid("model_stream_after_finish");
            var root=ModelJson.object(data,8192);var choices=root.path("choices");if(!choices.isArray()||choices.size()!=1)throw new Invalid("model_stream_choices");
            var choice=choices.get(0);var delta=choice.path("delta");if(!delta.isObject())throw new Invalid("model_stream_delta");
            if(delta.hasNonNull("role")&&!delta.path("role").asText().equals("assistant"))throw new Invalid("model_stream_role");
            for(String key:new String[]{"function_call","refusal"})if(delta.hasNonNull(key))throw new Invalid("model_stream_tools");
            if(delta.hasNonNull("reasoning_content")&&(!delta.path("reasoning_content").isString()||!delta.path("reasoning_content").asText().isEmpty()))throw new Invalid("model_stream_reasoning");
            var tools=delta.path("tool_calls");if(delta.hasNonNull("tool_calls")&&(!tools.isArray()||!tools.isEmpty()))throw new Invalid("model_stream_tools");
            if(delta.hasNonNull("content")){if(!delta.path("content").isString())throw new Invalid("model_stream_content");content.append(delta.path("content").asText());if(content.length()>characterLimit)throw new Invalid("model_stream_content_oversized");}
            if(choice.hasNonNull("finish_reason")){if(!choice.path("finish_reason").asText().equals("stop"))throw new Invalid("model_stream_truncated");finished=true;}
        }
        if(!done||!finished||content.isEmpty())throw new Invalid("model_stream_unfinished_done_"+done+"_stop_"+finished+"_chars_"+content.length()+"_events_"+events);return content.toString().strip();
    }
}
