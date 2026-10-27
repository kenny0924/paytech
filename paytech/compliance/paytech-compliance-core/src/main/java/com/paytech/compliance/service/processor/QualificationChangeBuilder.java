package com.paytech.compliance.service.processor;

import java.time.Instant;
import java.time.ZoneOffset;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationReviewStatus;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;

/**
 * 由一条新决定构造角色资格变更：目标投影值、没有投影时的新投影，以及一条状态变更
 * <li>入驻审核结论、手工登记与撤销共用；操作人取决定人，时间取决定时间，触发标识为决定 ID。</li>
 * <li>目标投影冻结决定的档次、评级、辖区与责任方；案件批准且程序有复审间隔时排定下次复审（只登记，本期不触发）。</li>
 * <li>纯内存构造，不访问数据库；版本与唯一性由保存时的条件更新和唯一键保证。</li>
 */
final class QualificationChangeBuilder {
    private QualificationChangeBuilder() {
    }

    /**
     * 构造资格变更
     *
     * @param ids 标识生成器
     * @param decision 已构造的新决定
     * @param current 读取时的投影；没有时为空
     * @param reasonCode 状态变更原因代码
     * @param triggerType 触发类型
     * @param reviewIntervalMonths 案件决定适用的复审间隔月数；手工决定或不复审为空
     * @return 资格变更上下文
     */
    static QualificationChangeContext change(ComplianceIdentifierGenerator ids, QualificationDecision decision,
        RoleQualification current, String reasonCode, String triggerType, Integer reviewIntervalMonths) {
        RoleQualification target = target(decision, current, reviewIntervalMonths);
        RoleQualification created = null;
        if (current == null) {
            created = target;
            created.setQualificationId(ids.nextIdentifier());
            created.setRoleType(decision.getRoleType());
            created.setRoleId(decision.getRoleId());
            created.setQualificationType(decision.getQualificationType());
            created.setVersion(1);
            created.setCreateTime(decision.getDecidedTime());
            created.setUpdateTime(decision.getDecidedTime());
        }
        QualificationStateChange change = new QualificationStateChange();
        change.setChangeId(ids.nextIdentifier());
        change.setQualificationId(current == null ? created.getQualificationId() : current.getQualificationId());
        change.setFromStatus(current == null ? null : current.getStatus());
        change.setToStatus(decision.getOutcome());
        change.setDecisionId(decision.getDecisionId());
        change.setReasonCode(reasonCode);
        change.setTriggerType(triggerType);
        change.setTriggerId(Long.toString(decision.getDecisionId()));
        change.setActorId(decision.getDecidedBy());
        change.setAggregateVersion(current == null ? 1 : current.getVersion() + 1);
        change.setOccurredTime(decision.getDecidedTime());
        change.setCreateTime(decision.getDecidedTime());
        return new QualificationChangeContext(decision, current, created, target, change);
    }

    /** 决定对应的目标投影值。 */
    static RoleQualification target(QualificationDecision decision, RoleQualification current, Integer reviewIntervalMonths) {
        boolean approved = QualificationStatus.APPROVED.getCode().equals(decision.getOutcome());
        Integer interval = approved ? reviewIntervalMonths : null;
        RoleQualification row = new RoleQualification();
        row.setCurrentDecisionId(decision.getDecisionId());
        row.setStatus(decision.getOutcome());
        row.setValidFrom(decision.getValidFrom());
        row.setValidTo(decision.getValidTo());
        row.setReviewIntervalMonths(interval);
        row.setNextReviewTime(interval == null ? null : plusMonths(decision.getDecidedTime(), interval));
        row.setReviewStatus(reviewStatus(approved, interval).getCode());
        row.setTier(decision.getTier());
        row.setRiskRating(decision.getRiskRating());
        row.setJurisdictionCode(decision.getJurisdictionCode());
        row.setResponsiblePartnerId(decision.getResponsiblePartnerId());
        row.setLastReviewTime(decision.getCaseId() != null ? decision.getDecidedTime()
            : current == null ? null : current.getLastReviewTime());
        return row;
    }

    /** 批准且有复审间隔为 ACTIVE，批准无间隔为 NOT_REQUIRED，其余为 COMPLETED。 */
    static QualificationReviewStatus reviewStatus(boolean approved, Integer interval) {
        if (!approved) {
            return QualificationReviewStatus.COMPLETED;
        }
        return interval == null ? QualificationReviewStatus.NOT_REQUIRED : QualificationReviewStatus.ACTIVE;
    }

    /** UTC 日历加月。 */
    static Instant plusMonths(Instant time, int months) {
        return time.atOffset(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
