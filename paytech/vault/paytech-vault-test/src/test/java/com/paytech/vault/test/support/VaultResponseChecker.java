package com.paytech.vault.test.support;

import com.paytech.foundation.common.error.FailException;
import com.paytech.vault.client.ProtectResult;
import com.paytech.vault.client.RevealResult;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

/** 仅断言安全语义，失败报告不渲染敏感 expected/actual。 */
public final class VaultResponseChecker {
    private VaultResponseChecker() {}

    public static void sameToken(ProtectResult first, ProtectResult second) {
        assertTrue(first.token().equals(second.token()), "幂等或 Stable Token 不一致");
    }

    public static void value(String expected, RevealResult result) {
        assertTrue(expected.equals(result.value()), "Reveal 未返回精确原值");
    }

    public static void failure(String code, Executable operation) {
        var failure = assertThrows(FailException.class, operation);
        assertTrue(code.equals(failure.getCode()), "Vault 拒绝 Code 不匹配");
        assertNull(failure.getCause(), "Vault 拒绝不得保留敏感 Cause");
    }
}
