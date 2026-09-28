package com.paytech.platform.support.test.notification.http;

import com.paytech.platform.support.test.notification.NotificationVaultMock;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.test.context.TestComponent;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.*;
import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务实现正式 Vault Wire；不装入 Platform 路由。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class NotificationVaultController implements VaultHttpService {

    private final NotificationVaultMock mock;

    /** 验证本服务及邮件专用 dataType/Purpose。 */
    @Override
    public ProtectResponse protect(String caller, String key, String requestId, ProtectRequest request) {
        require("platform-support-service".equals(caller) && "EMAIL_ADDRESS_V1".equals(request.dataType())
            && "STABLE".equals(request.tokenMode()) && "PLATFORM_NOTIFICATION_EMAIL_V1".equals(request.purpose()));
        return new ProtectResponse(mock.protect(request.value()));
    }

    /** 只读取邮件专用用途，不伪造其他 Vault 能力。 */
    @Override
    public RevealResponse reveal(String caller, String requestId, RevealRequest request) {
        require("platform-support-service".equals(caller) && "PLATFORM_NOTIFICATION_EMAIL_V1".equals(request.purpose()));
        return new RevealResponse(mock.reveal(request.token()));
    }

    /** 非预期协议失败由公共 Mock HTTP 服务记录并在 teardown 检查。 */
    void require(boolean valid) { if (!valid) throw new IllegalStateException("通知 Vault 协议不匹配"); }
}
