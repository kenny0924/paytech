package com.paytech.channel.connector.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.*;

import lombok.AllArgsConstructor;

/**
 * 仅存在于独立测试 HTTP 服务的 Vault 契约实现：channel-connector 读取渠道账号配置（含渠道协议执行的 CHANNEL_ACCOUNT_CONFIG 初始化）与写入账号配置
 * <li>Mock 服务只能有一个 VaultHttpService 实现，全部 Vault 场景委托同一个 VaultMocker。</li>
 */
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

    /** 账号配置写入的 Protect：按 Mocker 预设返回 Token 或故障，未配置记为意外。 */
    @Override
    public ProtectResponse protect(String caller, String key, String requestId, ProtectRequest request) {
        String errorRequestId = vault.protectErrorRequestId();
        if (errorRequestId != null) {
            var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            attributes.getResponse().setHeader("X-Request-Id", errorRequestId);
        }
        return vault.protect(caller, key, request);
    }
}
