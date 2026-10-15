package com.paytech.platform.support.service.processor.webhook;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTenantQueryService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService.ManagementContext;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.domain.WebhookEndpointUrl;
import com.paytech.platform.support.delivery.integration.webhook.WebhookAddressException;
import com.paytech.platform.support.delivery.integration.webhook.WebhookAddressGuard;

import lombok.AllArgsConstructor;

/**
 * Webhook 管理处理器共用的鉴权、事务、锁、审计与地址校验
 * <li>Delivery 模块不依赖鉴权与审计模块，由 Web 组合两者，Delivery 只负责本库读写与规则</li>
 * <li>写事务内只读写本库：会话与权限重检、配置写入和审计事实同一事务提交</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class WebhookManagementSupport {

    private final Clock clock;
    private final WebhookAddressGuard guard;
    private final OperationsAuditService audit;
    private final WebhookProperties properties;
    private final ClientTenantQueryService tenants;
    private final PlatformTransactionManager transactionManager;
    private final OperationsManagementPolicyService policy;

    /** 同一 Client 的新建端点串行，保护端点数量上限。 */
    static String clientLockKey(long tenantId) {
        return "webhook-client:" + tenantId;
    }

    /** 同一端点的修改、启停与轮换串行。 */
    static String endpointLockKey(long endpointId) {
        return "webhook-endpoint:" + endpointId;
    }

    /** TRY_LOCK 等待毫秒数。 */
    int lockWaitMillis() {
        return Math.toIntExact(properties.getManagementLockWaitTime().toMillis());
    }

    /** 锁租约毫秒数。 */
    int lockLeaseMillis() {
        return Math.toIntExact(properties.getManagementLockLeaseTime().toMillis());
    }

    /** 与 MySQL DATETIME(6) 保持 UTC 微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Client 必须已开通平台访问。 */
    long requireClientTenant(long clientId) {
        Long tenantId = tenants.findClientTenantId(clientId);
        if (tenantId == null) {
            throw new BizCheckFailException("Client 未开通平台访问: " + clientId);
        }
        return tenantId;
    }

    /**
     * 只读查询：会话权限与多表读取同一快照
     *
     * @param permission 所需权限
     */
    <T> T read(String actor, String session, String permission, Supplier<T> query) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction.execute(status -> {
            audit.observeTransaction();
            policy.requirePermission(actor, session, permission);
            return query.get();
        });
    }

    /**
     * 事务外调用 Vault、DNS 或发送测试事件前先确认管理权限，避免无权限请求触发外部调用
     * <li>不开事务：同一请求的审计只能观察一个事务，写事务内仍会重检权限</li>
     *
     * @return 当前会话上下文
     */
    SessionContext requireManage(String actor, String session) {
        return policy.requirePermission(actor, session, WebhookManagementPermissions.MANAGE);
    }

    /** 没有配置差异的操作（Replay、测试事件）记录成功事实，使审计带上操作者。 */
    void recordSucceeded(String actor, Long tenantId, String targetType, long targetId, String reason, Instant now) {
        audit.record(() -> Outcome.succeeded(actor, tenantId, new Target(targetType, Long.toString(targetId)), reason,
            now));
    }

    /**
     * 校验地址语法并解析 DNS，全部地址都必须是公网地址；在事务外调用
     *
     * @return 已校验的地址
     */
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

    /**
     * 写入：同一事务内重检管理权限、调用 Delivery 写入并记录配置审计
     *
     * @param reason 操作原因
     * @param write 以授权上下文与本次写入时间执行 Delivery 写入
     * @param change 从写入结果取出审计用的配置差异
     * @return Delivery 写入结果
     */
    <T> T write(String actor, String session, String reason, BiFunction<ManagementContext, Instant, T> write,
        Function<T, WebhookConfigurationChange> change) {
        return transaction(actor, session, (context, now) -> {
            T result = write.apply(context, now);
            record(actor, context, reason, change.apply(result), now);
            return result;
        });
    }

    /**
     * Replay：同一事务内重检管理权限、生成新投递并记录成功事实（对象为原投递）
     *
     * @param replay 以授权上下文与本次写入时间生成新投递，返回新投递 ID
     * @param originalDeliveryId 原投递 ID
     * @return 新投递 ID
     */
    long replay(String actor, String session, String reason, BiFunction<ManagementContext, Instant, Long> replay,
        long originalDeliveryId) {
        return transaction(actor, session, (context, now) -> {
            long deliveryId = replay.apply(context, now);
            recordSucceeded(actor, context.member().tenantId(), "WEBHOOK_DELIVERY", originalDeliveryId, reason, now);
            return deliveryId;
        });
    }

    /** 读已提交事务：观察审计、重检管理权限后执行；乐观并发失败转为 409。 */
    <T> T transaction(String actor, String session, BiFunction<ManagementContext, Instant, T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        try {
            return transaction.execute(status -> {
                audit.observeTransaction();
                var context = policy.authorizeManagement(actor, session, null, List.of(), null, null, false,
                    WebhookManagementPermissions.MANAGE);
                return work.apply(context, now());
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 配置审计；字段差异在写入成功后冻结。 */
    void record(String actor, ManagementContext context, String reason, WebhookConfigurationChange change,
        Instant now) {
        var fact = new ConfigurationChanged(new Identity(actor, context.member().tenantId()),
            new Target("WEBHOOK_ENDPOINT", Long.toString(change.getEndpointId())), change.getBeforeVersion(),
            change.getAfterVersion(), change.getChanges().stream().map(c -> new ConfigurationFieldChange(
                ConfigurationField.valueOf(c.getField().getCode()), c.getBefore(), c.getAfter())).toList(),
            reason, now);
        audit.record(() -> fact);
    }
}
