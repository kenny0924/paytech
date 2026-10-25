package com.paytech.compliance.service.processor;

import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.QualificationStateChange;
import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.domain.QualificationChangeContext;
import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;

/**
 * 由一条新决定构造角色资格变更：没有投影时准备版本 1 的新投影，并准备一条状态变更
 * <li>入驻审核结论、手工登记与撤销共用；操作人取决定人，时间取决定时间，触发标识为决定 ID。</li>
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
     * @return 资格变更上下文
     */
    static QualificationChangeContext change(ComplianceIdentifierGenerator ids, QualificationDecision decision,
        RoleQualification current, String reasonCode, String triggerType) {
        RoleQualification created = current == null ? newQualification(ids.nextIdentifier(), decision) : null;
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
        return new QualificationChangeContext(decision, current, created, change);
    }

    /** 版本 1 的新投影，状态与有效期取自决定，不安排持续复审。 */
    static RoleQualification newQualification(long qualificationId, QualificationDecision decision) {
        RoleQualification row = new RoleQualification();
        row.setQualificationId(qualificationId);
        row.setRoleType(decision.getRoleType());
        row.setRoleId(decision.getRoleId());
        row.setQualificationType(decision.getQualificationType());
        row.setCurrentDecisionId(decision.getDecisionId());
        row.setStatus(decision.getOutcome());
        row.setValidFrom(decision.getValidFrom());
        row.setValidTo(decision.getValidTo());
        row.setReviewStatus(QualificationStatus.ofCode(decision.getOutcome()).reviewStatus().getCode());
        row.setVersion(1);
        row.setCreateTime(decision.getDecidedTime());
        row.setUpdateTime(decision.getDecidedTime());
        return row;
    }
}
