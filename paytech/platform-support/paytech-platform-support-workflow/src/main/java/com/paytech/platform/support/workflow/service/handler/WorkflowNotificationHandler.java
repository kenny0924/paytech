package com.paytech.platform.support.workflow.service.handler;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelInfo;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsPersonnelService;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;
import com.paytech.platform.support.workflow.core.configuration.WorkflowProperties;
import com.paytech.platform.support.workflow.domain.WorkflowCallbackDelivery;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowCallbackService;

/**
 * 工作流 NOTIFY 的邮件投递（技术方案 4.2）：事务外按收件人账号读取登录邮箱 Token、Reveal 后经 Platform Delivery 受理一封普通通知。
 * <li>Delivery 来源身份为 platform-support + workflow-callback:{callbackId}，同一记录重复提交时 Delivery 拒绝重复受理，视为已送达。</li>
 * <li>收件人已不是 ACTIVE 的 Operations 账号时跳过；全部不可用视为本次失败。</li>
 * <li>失败计入 attempt_count，达到 workflow.callback-max-attempts 置 FAILED 并结束任务；邮箱与 Token 不进入日志或错误摘要。</li>
 * <li>受理失败记录可外显的业务原因（系统异常只记错误码与类型）并输出一条 WARN，只含回调、实例与触发标识。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowNotificationHandler implements CompensationEventHandler {

    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "WORKFLOW_NOTIFICATION";

    /** Delivery 来源系统。 */
    static final String SOURCE_SYSTEM = "platform-support";

    /** 邮件语言，首期固定中文。 */
    static final String LOCALE = "zh-CN";

    /** 失败摘要上限；存储列另有 512 截断。 */
    static final int SUMMARY_LIMIT = 200;

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowNotificationHandler.class);

    private static final Pattern EMAIL_LIKE = Pattern.compile("[^\\s@:：]+@[^\\s@]+");

    private final Clock clock;
    private final LoginEmailVault vault;
    private final WorkflowProperties properties;
    private final WorkflowCallbackService service;
    private final OperationsPersonnelService personnel;
    private final ObjectProvider<SubmitNotificationProcessor> notifications;

    /**
     * 装配依赖；邮箱 Reveal 使用 Operations 登录邮箱的 Vault 适配器。
     *
     * @param clock 时钟
     * @param vault Operations 登录邮箱 Vault
     * @param properties 工作流配置
     * @param service 通知回调服务
     * @param personnel 人员窄查询
     * @param notifications Delivery 普通通知入口；notification.enabled 关闭时不存在
     */
    public WorkflowNotificationHandler(Clock clock, @Qualifier("loginEmailVault") LoginEmailVault vault,
        WorkflowProperties properties, WorkflowCallbackService service, OperationsPersonnelService personnel,
        ObjectProvider<SubmitNotificationProcessor> notifications) {
        this.clock = clock;
        this.vault = vault;
        this.properties = properties;
        this.service = service;
        this.personnel = personnel;
        this.notifications = notifications;
    }

    /**
     * 纯工厂：与通知回调记录同事务登记。
     *
     * @param callbackId 通知回调 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long callbackId) {
        if (callbackId <= 0) {
            throw new IllegalArgumentException("通知回调 ID 必须为正数");
        }
        return new CompensationEventRequest(Long.toString(callbackId), EVENT_TYPE,
            WorkflowCallbackHandlerSupport.NO_SUB_EVENT_TYPE);
    }

    /** 持久化任务类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次 20 秒；任务次数上限大于业务上限，由业务记录决定何时置 FAILED。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 30, Duration.ofSeconds(5), Duration.ofMinutes(10),
            2, 0.2);
    }

    /** 事务外读取事实并发送；已不待投递的记录直接结束。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("工作流通知投递不能处于已有事务中");
        }
        Long callbackId = WorkflowCallbackHandlerSupport.callbackId(event);
        if (callbackId == null) {
            return CompensationHandlerResult.terminalFailure("WORKFLOW_NOTIFICATION_INVALID", "工作流通知任务身份不合法");
        }
        WorkflowCallbackDelivery delivery = service.load(callbackId);
        if (WorkflowCallbackHandlerSupport.finished(delivery)) {
            return CompensationHandlerResult.success();
        }
        String failure = send(delivery);
        if (failure != null) {
            return WorkflowCallbackHandlerSupport.failed(service, delivery, "WORKFLOW_NOTIFICATION_FAILED", failure,
                properties.getCallbackMaxAttempts(), clock);
        }
        service.markSent(delivery.getCallback(), WorkflowCallbackHandlerSupport.now(clock));
        return CompensationHandlerResult.success();
    }

    /**
     * 受理一封通知；成功或已受理返回 null，否则返回脱敏失败原因。
     *
     * @param delivery 投递事实
     * @return 失败原因或 null
     */
    String send(WorkflowCallbackDelivery delivery) {
        SubmitNotificationProcessor processor = notifications.getIfAvailable();
        if (processor == null) {
            return "通知模块未启用";
        }
        try {
            List<SubmitNotificationRequest.Recipient> recipients = recipients(delivery);
            if (recipients.isEmpty()) {
                return "没有可用的收件人";
            }
            processor.process(request(delivery, recipients));
            return null;
        } catch (ConcurrentRequestException alreadyAccepted) {
            // Delivery 已受理同一来源身份：上次受理成功但回写前中断。
            return null;
        } catch (RuntimeException unavailable) {
            String summary = "通知受理失败：" + failureSummary(unavailable);
            // 不带异常堆栈：系统异常的消息与 Cause 可能含收件地址。
            LOG.warn("工作流通知受理失败, callbackId={}, instanceId={}, trigger={}, previousAttempts={}, exceptionType={},"
                + " summary={}", delivery.getCallback().getCallbackId(), delivery.getCallback().getInstanceId(),
                delivery.getCallback().getTriggerCode().getCode(), delivery.getCallback().getAttemptCount(),
                unavailable.getClass().getName(), summary);
            return summary;
        }
    }

    /**
     * 失败摘要：业务与参数异常的消息按约定可安全外显，取其消息；系统异常只取错误码与类型，不取消息。
     * 摘要中形如邮箱的片段一律遮盖，长度截断到 {@value #SUMMARY_LIMIT}。
     *
     * @param failure 异常
     * @return 脱敏摘要
     */
    static String failureSummary(RuntimeException failure) {
        String text;
        if (failure instanceof FailException fail && fail.getMessage() != null && !fail.getMessage().isBlank()) {
            text = fail.getCode() + " " + fail.getMessage();
        } else if (failure instanceof ErrorException error) {
            text = error.getCode() + " " + failure.getClass().getSimpleName();
        } else {
            text = failure.getClass().getSimpleName();
        }
        String masked = EMAIL_LIKE.matcher(text).replaceAll("***");
        return masked.length() <= SUMMARY_LIMIT ? masked : masked.substring(0, SUMMARY_LIMIT);
    }

    /**
     * 仍为 ACTIVE 的收件人，Reveal 登录邮箱。
     *
     * @param delivery 投递事实
     * @return 收件人
     */
    List<SubmitNotificationRequest.Recipient> recipients(WorkflowCallbackDelivery delivery) {
        Map<Long, OperationsPersonnelInfo> found = personnel.personnel(delivery.getTarget().recipientIds());
        List<SubmitNotificationRequest.Recipient> result = new ArrayList<>();
        for (Long accountId : delivery.getTarget().recipientIds()) {
            OperationsPersonnelInfo info = found.get(accountId);
            if (info == null || !info.active() || info.emailToken() == null) {
                continue;
            }
            SubmitNotificationRequest.Recipient recipient = new SubmitNotificationRequest.Recipient();
            recipient.setEmail(vault.revealCanonicalEmail(info.emailToken()));
            result.add(recipient);
        }
        return result;
    }

    /**
     * 普通通知请求：模板参数为实例标题、节点名称与触发事件。
     *
     * @param delivery 投递事实
     * @param recipients 收件人
     * @return 请求
     */
    SubmitNotificationRequest request(WorkflowCallbackDelivery delivery, List<SubmitNotificationRequest.Recipient> recipients) {
        SubmitNotificationRequest request = new SubmitNotificationRequest();
        request.setSourceSystem(SOURCE_SYSTEM);
        request.setSourceEventKey("workflow-callback:" + delivery.getCallback().getCallbackId());
        request.setTemplateCode(properties.getNotificationTemplateCode());
        request.setDefaultLocale(LOCALE);
        request.setDefaultParameters(Map.of(
            "instanceTitle", delivery.getInstance().getTitle(),
            "nodeName", delivery.getNodeName() == null ? "" : delivery.getNodeName(),
            "trigger", delivery.getCallback().getTriggerCode().getCode()));
        request.setRecipients(recipients);
        return request;
    }
}
