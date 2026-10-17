package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.integration.signing.OperationsTokenCryptography;

import lombok.AllArgsConstructor;

/** 使用既有认证 HMAC 派生不暴露敏感输入的 Operations 资源 Key。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsResourceKeyPreparation {
    private static final String CREATE_ACCOUNT_PURPOSE = "operations-resource-lock:create-account:v1";
    private static final String UNKNOWN_LOGIN_PURPOSE = "operations-resource-lock:login-attempt:v1";
    private static final String UNKNOWN_REFRESH_PURPOSE = "operations-resource-lock:refresh-attempt:v1";
    private static final Pattern POSITIVE_ID = Pattern.compile("[0-9]{1,19}");
    private static final Pattern ROLE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private final OperationsTokenCryptography cryptography;

    /** 规范账号资源标识，非法输入使用固定拒绝桶。 */
    public String accountSegment(String candidate) {
        return positiveId(candidate, "INVALID_ACCOUNT_ID");
    }

    /** 规范会话资源标识。 */
    public String sessionSegment(String candidate) {
        return positiveId(candidate, "INVALID_SESSION_ID");
    }

    /** 规范角色资源标识。 */
    public String roleSegment(String candidate) {
        return positiveId(candidate, "INVALID_ROLE_ID");
    }

    /** 限定新角色编码的资源 Key 字符集。 */
    public String roleCodeSegment(String candidate) {
        return candidate != null && ROLE_CODE.matcher(candidate).matches() ? candidate : "INVALID_ROLE_CODE";
    }

    /** 对稳定邮箱 Token 派生受控资源摘要，不将 Token 原值写入 Redis Key。 */
    public String createAccountSegment(String emailToken) {
        return digest(CREATE_ACCOUNT_PURPOSE, emailToken);
    }

    /** 为未知登录标识派生不泄露邮箱的受控 HMAC 桶。 */
    public String unknownLoginSegment(String loginIdentifier) {
        return digest(UNKNOWN_LOGIN_PURPOSE, loginIdentifier);
    }

    /** 为未知刷新令牌派生不泄露秘密的受控 HMAC 桶。 */
    public String unknownRefreshSegment(String refreshToken) {
        return digest(UNKNOWN_REFRESH_PURPOSE, refreshToken);
    }

    /** 生成稳定正整数段或固定非法值占位。 */
    String positiveId(String candidate, String fallback) {
        if (candidate == null || !POSITIVE_ID.matcher(candidate).matches()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(candidate);
            return parsed > 0 ? Long.toString(parsed) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** 沿用批准的域分隔 HMAC，不使用原始秘密作为 Key。 */
    String digest(String purpose, String value) {
        try {
            var hmac = cryptography.versionedHmac(purpose, value == null ? "0:" : "1:" + value);
            if (hmac == null || hmac.keyVersion() < 1 || hmac.digest() == null
                || !DIGEST.matcher(hmac.digest()).matches()) {
                throw new IllegalStateException("认证 HMAC 返回了无效的资源摘要");
            }
            return hmac.keyVersion() + ":" + hmac.digest();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException(exception);
        }
    }
}
