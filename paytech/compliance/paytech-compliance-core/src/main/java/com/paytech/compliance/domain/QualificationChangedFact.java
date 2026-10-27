package com.paytech.compliance.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次已提交的角色资格状态迁移；QualificationChanged 事件的业务事实来源。 */
@Getter
@AllArgsConstructor
public class QualificationChangedFact {
    /** 角色资格 ID。 */
    private final long qualificationId;
    /** 迁移后的资格版本。 */
    private final long qualificationVersion;
    /** 角色类型。 */
    private final String roleType;
    /** 角色 ID。 */
    private final String roleId;
    /** 资格类型。 */
    private final String qualificationType;
    /** 迁移前状态；首次登记为空。 */
    private final String previousStatus;
    /** 迁移后状态。 */
    private final String status;
    /** 资格决定 ID。 */
    private final long decisionId;
    /** 决定来源。 */
    private final String decisionSource;
    /** 来源案件 ID；手工决定为空。 */
    private final Long caseId;
    /** 有效开始。 */
    private final Instant validFrom;
    /** 有效结束；无截止为空。 */
    private final Instant validTo;
    /** 冻结的档次。 */
    private final String tier;
    /** 冻结的风险评级。 */
    private final String riskRating;
    /** 冻结的辖区。 */
    private final String jurisdictionCode;
    /** 冻结的责任 PayFac。 */
    private final Long responsiblePartnerId;
    /** 迁移原因码。 */
    private final String reasonCode;
    /** 迁移时间。 */
    private final Instant changedAt;
}
