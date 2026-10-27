package com.paytech.compliance.service.handler;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.domain.QualificationChangedFact;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.QualificationStateChangeRepository;
import com.paytech.compliance.domainservice.repository.RoleQualificationRepository;
import com.paytech.compliance.integration.QualificationChangePublisher;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

import lombok.AllArgsConstructor;

/**
 * 角色资格状态迁移提交后发布 QualificationChanged
 * <li>eventKey 为资格 ID，subEventType 为迁移后的资格版本；Handler 回读该版本的状态变更与决定重建事实，补偿记录不保存业务内容。</li>
 * <li>案件决定、运营手工登记与撤销都在同一本地事务登记；消费方按事件 ID 防重。</li>
 */
@Component
@AllArgsConstructor
public class QualificationChangedHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "QUALIFICATION_CHANGED";

    private final QualificationChangePublisher publisher;
    private final QualificationDecisionRepository decisions;
    private final RoleQualificationRepository qualifications;
    private final QualificationStateChangeRepository stateChanges;

    /**
     * 纯工厂：只能与资格迁移在同一本地事务登记
     *
     * @param qualificationId 资格 ID
     * @param version 迁移后的资格版本
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long qualificationId, long version) {
        if (qualificationId <= 0 || version <= 0) {
            throw new IllegalArgumentException("Invalid qualification change identity");
        }
        return new CompensationEventRequest(Long.toString(qualificationId), EVENT_TYPE, Long.toString(version));
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, 0.2);
    }

    /**
     * 发布资格迁移
     * <li>资格、该版本状态变更或决定不存在为终态失败，保留记录供人工处理；Broker 未确认可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("QUALIFICATION_CHANGE_TRANSACTION_ACTIVE",
                "Qualification change must publish outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")
            || !event.subEventType().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("QUALIFICATION_CHANGE_INVALID", "Qualification change event is malformed");
        }
        QualificationChangedFact fact = fact(Long.parseLong(event.eventKey()), Long.parseLong(event.subEventType()));
        if (fact == null) {
            return CompensationHandlerResult.terminalFailure("QUALIFICATION_CHANGE_INVALID", "Qualification change is missing");
        }
        try {
            publisher.publish(fact);
            return CompensationHandlerResult.success();
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("QUALIFICATION_CHANGE_UNPUBLISHED",
                "Qualification change publication requires retry");
        }
    }

    /** 回读资格、指定版本状态变更与其决定；任一缺失返回 null。 */
    QualificationChangedFact fact(long qualificationId, long version) {
        RoleQualification qualification = qualifications.findById(qualificationId);
        QualificationStateChange change = qualification == null ? null : stateChanges.findByVersion(qualificationId, version);
        QualificationDecision decision = change == null ? null : decisions.findById(change.getDecisionId());
        if (decision == null) {
            return null;
        }
        return new QualificationChangedFact(qualificationId, version, qualification.getRoleType(), qualification.getRoleId(),
            qualification.getQualificationType(), change.getFromStatus(), change.getToStatus(), decision.getDecisionId(),
            decision.getDecisionSource(), decision.getCaseId(), decision.getValidFrom(), decision.getValidTo(), decision.getTier(),
            decision.getRiskRating(), decision.getJurisdictionCode(), decision.getResponsiblePartnerId(), change.getReasonCode(),
            change.getOccurredTime());
    }
}
