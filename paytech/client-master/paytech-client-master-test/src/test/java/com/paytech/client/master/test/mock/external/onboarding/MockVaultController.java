package com.paytech.client.master.test.mock.external.onboarding;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.test.mocker.onboarding.VaultMocker;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

import lombok.AllArgsConstructor;

/** 独立测试 HTTP 服务上的 Vault 契约：Protect 与 Reveal 都委托 Mocker；未配置的调用为意外调用（证件号从不 Reveal）。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockVaultController implements VaultHttpService {
    private final VaultMocker vault;

    /** 按 Mocker 配置返回稳定 Token、故障或意外调用。 */
    @Override
    public ProtectResponse protect(String caller, String idempotencyKey, String requestId, ProtectRequest request) {
        return MockPlatformAuthenticationApi.required(vault.protect(caller, request));
    }

    /** 只回显本例配置的联系方式 Token。 */
    @Override
    public RevealResponse reveal(String caller, String requestId, RevealRequest request) {
        return MockPlatformAuthenticationApi.required(vault.reveal(request));
    }
}
