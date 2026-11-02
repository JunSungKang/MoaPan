package com.moapan.identity.domain;


import java.util.Locale;

public final class CredentialPolicy {
    public static final String TERMS_VERSION="terms-1";
    private CredentialPolicy() {}
    public static final class Rejected extends IllegalArgumentException { public Rejected(String code){super(code);} }
    public static String username(String value) {
        if(value==null||!value.matches("[A-Za-z0-9_]{1,128}"))throw new Rejected("invalid_username");
        return value.toLowerCase(Locale.ROOT);
    }
    public static void password(String value) {
        if(value==null)throw new Rejected("invalid_password_length");
        int n=value.codePointCount(0,value.length());if(n<6||n>128)throw new Rejected("invalid_password_length");
    }
    public static String email(String value) {
        if(value==null)throw new Rejected("invalid_email");
        String address=value.strip().toLowerCase(Locale.ROOT);
        if(address.length()>254||address.indexOf('@')!=address.lastIndexOf('@')||address.indexOf('@')<1)throw new Rejected("invalid_email");
        var parts=address.split("@",-1);
        if(parts[0].length()>64||!parts[0].matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+")||parts[0].startsWith(".")||parts[0].endsWith(".")||parts[0].contains("..")
                ||!parts[1].matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+"))throw new Rejected("invalid_email");
        return address;
    }
}
