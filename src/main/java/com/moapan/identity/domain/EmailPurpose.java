package com.moapan.identity.domain;

/** Closed account-service purposes. Marketing, advertising and arbitrary bulk messages are unsupported. */
public enum EmailPurpose {
    SIGNUP_CODE, EMAIL_BIND_CODE, PASSWORD_RESET_CODE, PASSWORD_CHANGED, EMAIL_CHANGED
}
