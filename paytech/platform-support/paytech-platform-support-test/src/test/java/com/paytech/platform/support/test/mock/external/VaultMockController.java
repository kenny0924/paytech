package com.paytech.platform.support.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.test.dataquery.DataSourcePasswordVaultMock;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;
import com.paytech.platform.support.test.notification.NotificationVaultMock;
import com.paytech.platform.support.test.webhook.WebhookVaultMock;
import com.paytech.vault.client.internal.VaultHttpService;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务实现正式 Vault Wire，按 Purpose 委托对应 Mocker；不装入 Platform 路由。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class VaultMockController implements VaultHttpService {

    private static final String CALLER = "platform-support-service";
    private static final String EMAIL = "EMAIL_ADDRESS_V1";
    private static final String NOTIFICATION = "PLATFORM_NOTIFICATION_EMAIL_V1";
    private static final String LOGIN = "PLATFORM_LOGIN_EMAIL_V1";
    private static final String NOTIFICATION_DELIVERY = "NOTIFICATION_DELIVERY";
    private static final String CREDENTIAL = "NOTIFICATION_PROVIDER_CREDENTIAL_V1";
    private static final String PROVIDER_SETUP = "NOTIFICATION_PROVIDER_SETUP";
    private static final String WEBHOOK_SIGNING = "WEBHOOK_SIGNING";
    private static final String WEBHOOK_SECRET = "WEBHOOK_SIGNING_SECRET_V1";
    private static final String DATA_SOURCE_PASSWORD = "DATA_SOURCE_PASSWORD_V1";
    private static final String DATA_SOURCE_CONFIGURATION = "DATA_SOURCE_CONFIGURATION";
    private static final String DATA_SOURCE_CONNECTION = "DATA_SOURCE_CONNECTION";

    private final NotificationVaultMock notification;
    private final LoginEmailVaultMocker login;
    private final WebhookVaultMock webhook;
    private final DataSourcePasswordVaultMock dataSourcePasswords;

    /** 只接受本服务的邮箱稳定 Token 与发送账号密钥 REFERENCE 用途，其他用途作为意外请求失败。 */
    @Override
    public ProtectResponse protect(String caller, String key, String requestId, ProtectRequest request) {
        require(CALLER.equals(caller));
        if (WEBHOOK_SIGNING.equals(request.purpose())) {
            require(WEBHOOK_SECRET.equals(request.dataType()) && "REFERENCE".equals(request.tokenMode()));
            return new ProtectResponse(webhook.protect(request.value()));
        }
        if (DATA_SOURCE_CONFIGURATION.equals(request.purpose())) {
            require(DATA_SOURCE_PASSWORD.equals(request.dataType()) && "REFERENCE".equals(request.tokenMode()));
            return new ProtectResponse(dataSourcePasswords.protect(key, request.value()));
        }
        if (PROVIDER_SETUP.equals(request.purpose())) {
            require(CREDENTIAL.equals(request.dataType()) && "REFERENCE".equals(request.tokenMode()));
            return new ProtectResponse(notification.protectCredential(key, request.value()));
        }
        require(EMAIL.equals(request.dataType()) && "STABLE".equals(request.tokenMode()));
        return switch (request.purpose()) {
            case NOTIFICATION -> new ProtectResponse(notification.protect(request.value()));
            case LOGIN -> new ProtectResponse(login.protect(request.value()));
            default -> throw new IllegalStateException("Vault Protect 用途不匹配");
        };
    }

    /** 只读取本服务已批准的邮箱、通知发送账号凭据、Webhook 签名与数据源密码用途，不伪造其他 Vault 能力。 */
    @Override
    public RevealResponse reveal(String caller, String requestId, RevealRequest request) {
        require(CALLER.equals(caller));
        return switch (request.purpose()) {
            case NOTIFICATION -> new RevealResponse(notification.reveal(request.token()));
            case LOGIN -> new RevealResponse(login.reveal(request.token()));
            case NOTIFICATION_DELIVERY -> new RevealResponse(notification.revealCredential(request.token()));
            case WEBHOOK_SIGNING -> new RevealResponse(webhook.reveal(request.token()));
            case DATA_SOURCE_CONNECTION -> new RevealResponse(dataSourcePasswords.reveal(request.token()));
            default -> throw new IllegalStateException("Vault Reveal 用途不匹配");
        };
    }

    /** 非预期协议失败由公共 Mock HTTP 服务记录并在 teardown 检查。 */
    void require(boolean valid) {
        if (!valid) {
            throw new IllegalStateException("Vault 协议不匹配");
        }
    }
}
