package com.paytech.platform.support.api.apicredential;

import java.util.regex.Pattern;

/** PLT-I03 Credential 与 Scope 的稳定 wire 限制。 */
public final class ApiCredentialConstraints {

    public static final String CREDENTIAL_HEADER = "X-Platform-Api-Credential";
    public static final int MIN_KEY_PREFIX_LENGTH = 8;
    public static final int MAX_KEY_PREFIX_LENGTH = 32;
    public static final int MIN_SECRET_LENGTH = 32;
    public static final int MAX_SECRET_LENGTH = 223;
    public static final int MIN_CREDENTIAL_LENGTH = MIN_KEY_PREFIX_LENGTH + 1 + MIN_SECRET_LENGTH;
    public static final int MAX_CREDENTIAL_LENGTH = MAX_KEY_PREFIX_LENGTH + 1 + MAX_SECRET_LENGTH;
    public static final int MAX_EFFECTIVE_SCOPE_CODES = 64;
    public static final int MAX_SCOPE_CODE_LENGTH = 128;

    private static final Pattern KEY_PREFIX = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]{7,31}");
    private static final Pattern SECRET = Pattern.compile("[A-Za-z0-9_-]{32,223}");
    private static final Pattern SCOPE_CODE = Pattern.compile("[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*");

    private ApiCredentialConstraints() {}

    /** Gateway 与 Platform 共用的完整 opaque Credential wire 校验。 */
    public static boolean isValidCredential(String credential) {
        if (credential == null || credential.length() < MIN_CREDENTIAL_LENGTH
            || credential.length() > MAX_CREDENTIAL_LENGTH) {
            return false;
        }
        int separator = credential.indexOf('.');
        return separator == credential.lastIndexOf('.')
            && separator >= MIN_KEY_PREFIX_LENGTH && separator <= MAX_KEY_PREFIX_LENGTH
            && KEY_PREFIX.matcher(credential.substring(0, separator)).matches()
            && SECRET.matcher(credential.substring(separator + 1)).matches();
    }

    /** 仅供 Platform 在完整 wire 校验后定位有界候选；Prefix 不表达身份。 */
    public static String keyPrefix(String credential) {
        if (!isValidCredential(credential)) {
            throw new IllegalArgumentException("API Credential 格式无效");
        }
        return credential.substring(0, credential.indexOf('.'));
    }

    public static boolean isValidScopeCode(String scopeCode) {
        return scopeCode != null && scopeCode.length() <= MAX_SCOPE_CODE_LENGTH
            && SCOPE_CODE.matcher(scopeCode).matches();
    }
}
