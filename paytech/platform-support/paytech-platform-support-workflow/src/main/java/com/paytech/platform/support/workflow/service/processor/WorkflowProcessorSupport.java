package com.paytech.platform.support.workflow.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelInfo;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsPersonnelService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domain.enums.AuditActorType;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.workflow.core.configuration.WorkflowProperties;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.domain.WorkflowRuntimeChange;
import com.paytech.platform.support.workflow.core.enums.NotificationRuleType;
import com.paytech.platform.support.workflow.service.handler.WorkflowCallbackEventHandler;
import com.paytech.platform.support.workflow.service.handler.WorkflowNotificationHandler;

/**
 * 工作流各入口共用的本地能力：权限重检与审计身份、本地写事务、锁参数、人员核对与通知回调的补偿任务构造。
 * 不访问外部服务。
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowProcessorSupport {

    private final Clock clock;
    private final IdGenerator ids;
    private final WorkflowProperties properties;
    private final OperationsAuditService audit;
    private final TransactionTemplate write;
    private final OperationsPersonnelService personnel;
    private final OperationsManagementPolicyService policy;

    /**
     * 装配依赖并固定写事务：读已提交、15 秒超时，与 Operations 其他写入口一致。
     *
     * @param clock 时钟
     * @param ids ID 生成器
     * @param properties 工作流配置
     * @param audit Operations 审计
     * @param manager 本库事务管理器
     * @param personnel 人员窄查询
     * @param policy 权限重检
     */
    public WorkflowProcessorSupport(Clock clock, IdGenerator ids, WorkflowProperties properties,
        OperationsAuditService audit, @Qualifier("transactionManager") PlatformTransactionManager manager,
        OperationsPersonnelService personnel, OperationsManagementPolicyService policy) {
        this.clock = clock;
        this.ids = ids;
        this.properties = properties;
        this.audit = audit;
        this.personnel = personnel;
        this.policy = policy;
        this.write = new TransactionTemplate(manager);
        this.write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.write.setTimeout(15);
    }

    /**
     * 在本库写事务中执行，并在事务开始后登记审计完成通知。
     *
     * @param operation 本地操作
     * @param <T> 结果类型
     * @return 结果
     */
    public <T> T write(Supplier<T> operation) {
        return write.execute(status -> {
            audit.observeTransaction();
            return operation.get();
        });
    }

    /**
     * 重检当前会话至少持有一项权限，并登记审计身份。
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param permissions 所需权限之一
     * @return 当前会话
     */
    public SessionContext authorize(String actor, String session, String... permissions) {
        SessionContext context = policy.requirePermission(actor, session, permissions);
        audit.identify(() -> new Identity(actor, context.member().tenantId(), AuditActorType.USER));
        return context;
    }

    /**
     * 登记本次成功变更的审计事实；资源类型须属于入口声明的审计资源，事务提交后才会落审计。
     *
     * @param context 当前会话
     * @param targetType 审计资源类型
     * @param targetId 资源 ID
     * @param reason 有界结果编码
     */
    public void succeeded(SessionContext context, String targetType, long targetId, String reason) {
        Instant now = now();
        String actor = Long.toString(actorId(context));
        Long tenantId = context.member().tenantId();
        audit.record(() -> Outcome.succeeded(actor, tenantId, new Target(targetType, Long.toString(targetId)), reason,
            now));
    }

    /**
     * 当前会话是否持有某项权限。
     *
     * @param context 当前会话
     * @param permission 权限
     * @return 持有返回 true
     */
    public boolean has(SessionContext context, String permission) {
        return policy.hasPermission(context, permission);
    }

    /**
     * 当前账号 ID。
     *
     * @param context 当前会话
     * @return 账号 ID
     */
    public long actorId(SessionContext context) {
        return context.account().id();
    }

    /** 下一个 Snowflake ID。 */
    public long nextId() {
        return ids.nextId();
    }

    /** 本次操作的 UTC 微秒时间。 */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** TRY_LOCK 等待毫秒数。 */
    public int lockWaitMillis() {
        return Math.toIntExact(properties.getLockWaitTime().toMillis());
    }

    /** 资源锁租约毫秒数。 */
    public int lockLeaseMillis() {
        return Math.toIntExact(properties.getLockLeaseTime().toMillis());
    }

    /**
     * 要求人员都是 ACTIVE 的 Operations 账号。
     *
     * @param accountIds 账号
     * @param role 角色说明，如“审批人”
     */
    public void requireActive(Collection<Long> accountIds, String role) {
        Map<Long, OperationsPersonnelInfo> found = personnel.personnel(accountIds);
        List<Long> inactive = accountIds.stream().filter(id -> !found.containsKey(id) || !found.get(id).active())
            .distinct().toList();
        if (!inactive.isEmpty()) {
            throw new BizCheckFailException(role + "不是 ACTIVE 的 Operations 账号：" + inactive);
        }
    }

    /**
     * 人员中属于 ACTIVE Operations 账号的部分。
     *
     * @param accountIds 账号
     * @return ACTIVE 账号
     */
    public Set<Long> activeAmong(Collection<Long> accountIds) {
        Set<Long> result = new LinkedHashSet<>();
        personnel.personnel(accountIds).values().stream().filter(OperationsPersonnelInfo::active)
            .forEach(info -> result.add(info.accountId()));
        return result;
    }

    /**
     * 人员显示名（登录名，没有时为脱敏邮箱）；找不到的账号不出现。
     *
     * @param accountIds 账号
     * @return 十进制账号到显示名
     */
    public Map<String, String> displayNames(Collection<Long> accountIds) {
        Map<String, String> result = new LinkedHashMap<>();
        personnel.personnel(accountIds).forEach((id, info) -> result.put(Long.toString(id), info.displayName()));
        return result;
    }

    /**
     * 为本次新登记的通知回调构造补偿任务，与运行变化同事务登记。
     *
     * @param change 运行变化
     */
    public void registerCallbackEvents(WorkflowRuntimeChange change) {
        for (WorkflowCallback callback : change.getNewCallbacks()) {
            change.getCompensationEvents().add(callbackEvent(callback));
        }
    }

    /**
     * 一条通知回调对应的补偿任务：NOTIFY 走邮件 Handler，CALLBACK 走事件发布 Handler。
     *
     * @param callback 通知回调
     * @return 补偿任务登记请求
     */
    public CompensationEventRequest callbackEvent(WorkflowCallback callback) {
        return callback.getCallbackType() == NotificationRuleType.NOTIFY
            ? WorkflowNotificationHandler.buildEvent(callback.getCallbackId())
            : WorkflowCallbackEventHandler.buildEvent(callback.getCallbackId());
    }

    /**
     * 解析十进制字符串 ID。
     *
     * @param value 文本
     * @param name 名称
     * @return ID
     */
    public long parseId(String value, String name) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException(name + " 必须是十进制字符串 ID");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException(name + " 必须是十进制字符串 ID", exception);
        }
    }

    /**
     * 资源不存在。
     *
     * @return 公共 NOT_FOUND
     */
    public FailException notFound() {
        return new FailException(CommonErrorCode.NOT_FOUND);
    }
}
