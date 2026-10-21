package com.paytech.reconciliation.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.reconciliation.test.mocker.external.VaultMocker;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Vault 契约实现：Protect 委托 Mocker，Reveal 记为意外调用。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockVaultController implements VaultHttpService {
    private final VaultMocker vault;

    /** 按 Mocker 预设返回 Token 或故障。 */
    @Override
    public ProtectResponse protect(String caller, String idempotencyKey, String requestId, ProtectRequest request) {
        return vault.protect(caller, idempotencyKey, request);
    }

    /** 对账服务不读明文账号。 */
    @Override
    public RevealResponse reveal(String caller, String requestId, RevealRequest request) {
        vault.unexpectedReveal();
        return null;
    }
}
