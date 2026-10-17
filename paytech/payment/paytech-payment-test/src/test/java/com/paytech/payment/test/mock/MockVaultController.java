package com.paytech.payment.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.test.mocker.VaultMocker;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.*;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Vault 契约实现。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockVaultController implements VaultHttpService {
    private final VaultMocker vault;

    /** 通过实际 HTTP 请求返回按账号预设的 Reveal DTO。 */
    @Override
    public RevealResponse reveal(String caller, String requestId, RevealRequest request) {
        String errorRequestId = vault.errorRequestId(request.token());
        if (errorRequestId != null) {
            var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            attributes.getResponse().setHeader("X-Request-Id", errorRequestId);
        }
        return vault.reveal(caller, request.token(), request.purpose());
    }

    /** 只有卡 DEK 托管会发送 Protect，其余调用由 Mocker 记为意外。 */
    @Override
    public ProtectResponse protect(String caller, String key, String requestId, ProtectRequest request) {
        return vault.protect(caller, key, request);
    }
}
