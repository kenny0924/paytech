package com.paytech.payout.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payout.test.mocker.external.VaultMocker;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Vault 契约实现，委托 VaultMocker。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockVaultController implements VaultHttpService {
    private final VaultMocker vault;

    /** Protect。 */
    @Override
    public ProtectResponse protect(String caller, String idempotencyKey, String requestId, ProtectRequest request) {
        return vault.protect(caller, idempotencyKey, request);
    }

    /** Reveal。 */
    @Override
    public RevealResponse reveal(String caller, String requestId, RevealRequest request) {
        return vault.reveal(caller, request);
    }
}
