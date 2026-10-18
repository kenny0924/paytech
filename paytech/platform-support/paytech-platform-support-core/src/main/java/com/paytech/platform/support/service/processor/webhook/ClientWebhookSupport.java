package com.paytech.platform.support.service.processor.webhook;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientWorkspaceAccess;
import com.paytech.platform.support.accesscontrol.service.processor.ClientWorkspaceAccessSupport;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.platform.support.delivery.domain.WebhookEndpointUrl;
import com.paytech.platform.support.delivery.integration.webhook.WebhookAddressException;
import com.paytech.platform.support.delivery.integration.webhook.WebhookAddressGuard;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.delivery.service.WebhookQueryService;

import lombok.AllArgsConstructor;

/**
 * 工作区自助 Webhook 处理器共用的工作区重证、权限、归属校验、事务与审计
 * <li>工作区只来自当前有效的 CLIENT 租户上下文会话；按端点或投递 ID 的操作必须属于该工作区，否则 404。</li>
 * <li>写事务内只读写本库：会话与权限重检、归属复核、配置写入和审计事实同一事务提交。</li>
 * <li>与运营代配置共用 Delivery 服务与锁键，二者互斥；运营入口不受影响。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientWebhookSupport {

    /** 客户写操作写入审计的固定原因。 */
    static final String REASON = "CLIENT_SELF_SERVICE";

    private final Clock clock;
    private final WebhookAddressGuard guard;
    private final OperationsAuditService audit;
    private final WebhookProperties properties;
    private final WebhookQueryService query;
    private final WebhookManagementService management;
    private final ClientWorkspaceAccessSupport access;

    /** TRY_LOCK 等待毫秒数，与运营入口一致。 */
    int lockWaitMillis() {
        return Math.toIntExact(properties.getManagementLockWaitTime().toMillis());
    }

    /** 锁租约毫秒数，与运营入口一致。 */
    int lockLeaseMillis() {
        return Math.toIntExact(properties.getManagementLockLeaseTime().toMillis());
    }

    /** 与 MySQL DATETIME(6) 保持 UTC 微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 事务外预检：只读快照内重证工作区与权限，可选复核端点归属；用于锁键与外部调用前的拒绝。 */
    ClientWorkspaceAccess precheck(long accountId, long sessionId, String permission, Long endpointId) {
        return access.readTransaction().execute(status -> {
            var workspace = access.require(accountId, sessionId, permission);
            if (endpointId != null) {
                requireEndpoint(workspace, endpointId);
            }
            return workspace;
        });
    }

    /** 只读查询：会话、权限与多表读取同一快照。 */
    <T> T read(long accountId, long sessionId, String permission, Function<ClientWorkspaceAccess, T> query) {
        return access.readTransaction().execute(status -> query.apply(access.require(accountId, sessionId, permission)));
    }

    /** 写入：读已提交事务内观察审计、重证工作区与权限后执行；乐观并发失败转为 409。 */
    <T> T write(long accountId, long sessionId, String permission, BiFunction<ClientWorkspaceAccess, Instant, T> work) {
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                return work.apply(access.require(accountId, sessionId, permission), now());
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 端点必须属于当前工作区；不存在与不属于统一 404。 */
    void requireEndpoint(ClientWorkspaceAccess workspace, long endpointId) {
        if (management.endpointTenant(endpointId) != workspace.getTenantId()) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
    }

    /** 投递经其端点归属当前工作区；不存在与不属于统一 404。 */
    void requireDelivery(ClientWorkspaceAccess workspace, long deliveryId) {
        requireEndpoint(workspace, query.delivery(deliveryId).getEndpointId());
    }

    /** 记录客户写操作成功事实；对象为端点或投递。 */
    void record(ClientWorkspaceAccess workspace, String targetType, long targetId, String reason, Instant now) {
        audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
            new Target(targetType, Long.toString(targetId)), reason, now));
    }

    /** 校验地址语法并解析 DNS，全部地址必须是公网地址；在事务外调用。 */
    String requirePublicUrl(String url) {
        URI uri = WebhookEndpointUrl.parse(url, properties.getAllowedSchemes());
        try {
            guard.resolve(uri.getHost());
        } catch (WebhookAddressException rejected) {
            throw new BizCheckFailException(rejected.getFailureCode() == WebhookFailureCode.DNS_FAILED
                ? "端点主机名无法解析" : "端点地址指向内网或保留地址");
        }
        return url;
    }

    /** 宽限期配置，供轮换计算旧密钥截止。 */
    java.time.Duration secretGracePeriod() {
        return properties.getSecretGracePeriod();
    }
}
