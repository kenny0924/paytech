package com.paytech.platform.support.service.processor.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.configuration.NotificationManagementProperties;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;

import lombok.AllArgsConstructor;

/**
 * 通知管理处理器共用的鉴权、事务、锁与审计组合
 * <li>Delivery 模块不依赖鉴权与审计模块，由 Web 组合两者，Delivery 只负责本库读写与配置规则</li>
 * <li>事务内只读写本库：会话与权限重检、配置写入和审计事实同一事务提交</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class NotificationManagementSupport {

    private final Clock clock;
    private final OperationsAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final NotificationManagementProperties properties;
    private final OperationsManagementPolicyService policy;

    /** 模板及其语言内容、路由共用一把锁，避免启用模板与修改内容、路由交叉。 */
    static String templateLockKey(String templateCode) {
        return "notification-template:" + templateCode;
    }

    /** 发送账号启停按账号编码协调。 */
    static String providerLockKey(String providerCode) {
        return "notification-provider:" + providerCode;
    }

    /** TRY_LOCK 等待毫秒数。 */
    int lockWaitMillis() {
        return Math.toIntExact(properties.getLockWaitTime().toMillis());
    }

    /** 锁租约毫秒数。 */
    int lockLeaseMillis() {
        return Math.toIntExact(properties.getLockLeaseTime().toMillis());
    }

    /**
     * 只读查询：会话权限与多表读取同一快照，避免列表与总数、详情各表混用不同提交时点
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
     * 写入：同一事务内重检管理权限、调用 Delivery 写入并记录配置审计
     *
     * @param targetType 审计对象类型
     * @param targetCode 审计对象编码
     * @param write 以本次写入时间执行 Delivery 写入，返回字段差异
     * @return Delivery 写入结果
     */
    NotificationConfigurationChange write(String actor, String session, String targetType, String targetCode,
        String reason, Function<Instant, NotificationConfigurationChange> write) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        try {
            return transaction.execute(status -> writeWithinTransaction(actor, session, targetType, targetCode,
                reason, write));
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 授权、写入与审计事实；审计在写入成功后冻结差异。 */
    NotificationConfigurationChange writeWithinTransaction(String actor, String session, String targetType,
        String targetCode, String reason, Function<Instant, NotificationConfigurationChange> write) {
        audit.observeTransaction();
        var context = policy.authorizeManagement(actor, session, null, List.of(), null, null, false,
            NotificationManagementPermissions.MANAGE);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        NotificationConfigurationChange change = write.apply(now);
        var fact = new ConfigurationChanged(new Identity(actor, context.member().tenantId()),
            new Target(targetType, targetCode), change.getBeforeVersion(), change.getAfterVersion(),
            change.getChanges().stream().map(c -> new ConfigurationFieldChange(
                ConfigurationField.valueOf(c.getField().getCode()), c.getBefore(), c.getAfter())).toList(),
            reason, now);
        audit.record(() -> fact);
        return change;
    }
}
