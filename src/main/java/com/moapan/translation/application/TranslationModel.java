package com.moapan.translation.application;
public interface TranslationModel {
 boolean enabled(); boolean healthy();
 String translate(String input,String language,int attempt,String reason) throws Exception;
}
