package com.moapan.news;

/** Safe diagnostic codes only; never include input, model output, URL or credentials. */
public class TranslationFailure extends IllegalStateException {
    private final boolean infrastructure;
    public TranslationFailure(String code,boolean infrastructure){super(code);this.infrastructure=infrastructure;}
    public boolean infrastructure(){return infrastructure;}
}
