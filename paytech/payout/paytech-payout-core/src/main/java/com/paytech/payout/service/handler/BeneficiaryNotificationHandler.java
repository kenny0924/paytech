package com.paytech.payout.service.handler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.core.configuration.PayoutNotificationProperties;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryNotification;
import com.paytech.payout.domain.BeneficiaryNotificationSource;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.integration.BeneficiaryNotificationClient;

/**
 * 本地任务 BENEFICIARY_NOTIFICATION：收款账户核验通过 / 拒绝、停用、关闭后通知 Owner（R7、B10、AC-09）
 * <li>事件键 {@code {accountId}:{eventNo}}，业务子类型为 Platform 邮件模板编码；与状态变化在同一本地事务登记，提交后执行。</li>
 * <li>Handler 从本库重建通知：模板参数只有银行名、末四位、币种、可用时间（核验通过）与原因（代码，拒绝时附运营给 Owner 的说明），不含原值或 Token；
 * Platform 去重键 {@code BENEFICIARY:{accountId}:{eventNo}}，重试复用同一键。</li>
 * <li>Local Partner 没有门户与权限码，不发邮件：任务记 INFO 日志后成功，不调用 Platform。</li>
 * <li>Platform 409（该键已受理）视为成功；0 个收件人记录后成功；其他 4xx 终态失败保留任务；超时与 5xx 退避重试。通知失败不影响账户状态。</li>
 * <li>本地 Service 经 {@link ObjectProvider} 在执行时取得，避免与补偿登记器的启动循环。</li>
 */
@Component
public class BeneficiaryNotificationHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "BENEFICIARY_NOTIFICATION";

    /** 核验通过模板。 */
    public static final String TEMPLATE_VERIFIED = "PAYOUT_BENEFICIARY_VERIFIED_EMAIL";

    /** 核验拒绝模板。 */
    public static final String TEMPLATE_REJECTED = "PAYOUT_BENEFICIARY_REJECTED_EMAIL";

    /** 停用模板。 */
    public static final String TEMPLATE_SUSPENDED = "PAYOUT_BENEFICIARY_SUSPENDED_EMAIL";

    /** 关闭模板。 */
    public static final String TEMPLATE_CLOSED = "PAYOUT_BENEFICIARY_CLOSED_EMAIL";

    /** 合法事件键。 */
    static final Pattern KEY = Pattern.compile("([1-9][0-9]{0,18}):([1-9][0-9]{0,8})");

    /** 状态 → 模板。 */
    static final Map<BeneficiaryAccountStatus, String> TEMPLATES = Map.of(BeneficiaryAccountStatus.VERIFIED, TEMPLATE_VERIFIED,
        BeneficiaryAccountStatus.REJECTED, TEMPLATE_REJECTED, BeneficiaryAccountStatus.SUSPENDED, TEMPLATE_SUSPENDED,
        BeneficiaryAccountStatus.CLOSED, TEMPLATE_CLOSED);

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryNotificationHandler.class);

    private final BeneficiaryNotificationClient notificationClient;
    private final PayoutNotificationProperties notificationProperties;
    private final ObjectProvider<BeneficiaryAccountService> beneficiaryAccountService;

    /** 以通知出站端口、通知配置与延迟取得的本地服务构造。 */
    public BeneficiaryNotificationHandler(BeneficiaryNotificationClient notificationClient,
        PayoutNotificationProperties notificationProperties,
        ObjectProvider<BeneficiaryAccountService> beneficiaryAccountService) {
        this.notificationClient = notificationClient;
        this.notificationProperties = notificationProperties;
        this.beneficiaryAccountService = beneficiaryAccountService;
    }

    /**
     * 纯工厂：状态变化事务内登记
     *
     * @param beneficiaryAccountId 账户主键
     * @param eventNo 触发事件序号
     * @param templateCode 邮件模板编码
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long beneficiaryAccountId, int eventNo, String templateCode) {
        if (!TEMPLATES.containsValue(templateCode)) {
            throw new IllegalArgumentException("Unsupported beneficiary notification template");
        }
        return new CompensationEventRequest(beneficiaryAccountId + ":" + eventNo, EVENT_TYPE, templateCode);
    }

    /**
     * 状态对应的模板
     *
     * @param status 变化后的状态
     * @return 模板编码；该状态不通知时为 null
     */
    public static String templateFor(BeneficiaryAccountStatus status) {
        return TEMPLATES.get(status);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 25 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(25), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2,
            0.2);
    }

    /**
     * 重建并提交一次通知
     * <li>身份格式不合法、事实缺失为终态失败；Platform 明确拒绝为终态失败；不可用与结果未知为可重试失败。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("BENEFICIARY_NOTIFICATION_TRANSACTION_ACTIVE",
                "Beneficiary notification must run outside a transaction");
        }
        Matcher key = KEY.matcher(event.eventKey());
        if (!key.matches() || !TEMPLATES.containsValue(event.subEventType()) || event.payloadJson() != null) {
            return CompensationHandlerResult.terminalFailure("BENEFICIARY_NOTIFICATION_INVALID",
                "Beneficiary notification event is malformed");
        }
        BeneficiaryNotificationSource source = beneficiaryAccountService.getObject()
            .loadNotificationSource(Long.valueOf(key.group(1)), Integer.valueOf(key.group(2)));
        if (source == null) {
            return CompensationHandlerResult.terminalFailure("BENEFICIARY_NOTIFICATION_SOURCE_MISSING",
                "Beneficiary account or event not found");
        }
        if (source.getAccount().getOwnerType() == BeneficiaryOwnerType.LOCAL_PARTNER) {
            log.info("Local Partner 收款账户不发邮件通知，任务直接完成: {}", event.eventKey());
            return CompensationHandlerResult.success();
        }
        return submit(buildNotification(source, event.subEventType()));
    }

    /** 提交通知并按结果分类。 */
    CompensationHandlerResult submit(BeneficiaryNotification notification) {
        try {
            int deliveries = notificationClient.submit(notification);
            if (deliveries == 0) {
                log.info("收款账户通知没有符合条件的收件人，已跳过: {}", notification.getSourceEventKey());
            }
            return CompensationHandlerResult.success();
        } catch (ConcurrentRequestException alreadyAccepted) {
            return CompensationHandlerResult.success();
        } catch (FailException rejected) {
            return CompensationHandlerResult.terminalFailure("BENEFICIARY_NOTIFICATION_REJECTED",
                "Platform rejected the beneficiary notification");
        } catch (RuntimeException unavailable) {
            // 不把上游正文写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("BENEFICIARY_NOTIFICATION_UNAVAILABLE",
                "Beneficiary notification outcome requires retry");
        }
    }

    /**
     * 通知原因文字：触发事件的原因代码；拒绝通知另附运营填写的给 Owner 的说明（运营文字，不含原值）
     *
     * @param source 账户与触发事件
     * @param templateCode 模板编码
     * @return 原因文字；没有原因时为 null
     */
    static String reasonText(BeneficiaryNotificationSource source, String templateCode) {
        String reasonCode = source.getEvent().getReasonCode();
        String reasonNote = TEMPLATE_REJECTED.equals(templateCode) ? source.getAccount().getReasonNote() : null;
        if (reasonCode == null || reasonNote == null) {
            return reasonCode;
        }
        return reasonCode + ": " + reasonNote;
    }

    /** 从账户与事件构造通知：模板参数不含原值。 */
    BeneficiaryNotification buildNotification(BeneficiaryNotificationSource source, String templateCode) {
        BeneficiaryAccount account = source.getAccount();
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("bankName", account.getBankName());
        parameters.put("accountLast4", account.getAccountLast4());
        parameters.put("currency", account.getCurrency());
        if (TEMPLATE_VERIFIED.equals(templateCode) && account.getCoolingUntil() != null) {
            parameters.put("availableAt", account.getCoolingUntil().toString());
        }
        String reasonText = reasonText(source, templateCode);
        if (reasonText != null) {
            parameters.put("reasonText", reasonText);
        }
        return new BeneficiaryNotification("BENEFICIARY:" + account.getId() + ":" + source.getEvent().getEventNo(),
            templateCode, account.getOwnerType(), account.getOwnerId(),
            notificationProperties.getRecipientPermissions().get(account.getOwnerType().getCode()),
            notificationProperties.getDefaultLocale(), parameters);
    }
}
