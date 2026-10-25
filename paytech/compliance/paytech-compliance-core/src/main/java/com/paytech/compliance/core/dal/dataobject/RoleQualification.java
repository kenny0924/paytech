package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import com.paytech.compliance.core.enums.QualificationEvaluationStatus;
import com.paytech.compliance.core.enums.QualificationStatus;

import lombok.Data;

/**
 * 角色资格投影（t_role_qualification）：每个角色与资格类型当前生效的决定及有效期
 * <li>唯一键 role_type + role_id + qualification_type；写入以 version 条件更新串行。</li>
 */
@Data
public class RoleQualification {
    /** 角色资格 ID。 */
    private long qualificationId;

    /** 角色类型：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY。 */
    private String roleType;

    /**
     * 角色标识
     * <li>OPERATING_ENTITY 为 entity_no；其余为十进制 ID 文本。</li>
     */
    private String roleId;

    /** 资格类型。 */
    private String qualificationType;

    /** 当前生效的资格决定 ID。 */
    private long currentDecisionId;

    /** 资格状态：APPROVED、REJECTED、REVOKED、EXPIRED。 */
    private String status;

    /** 有效开始 UTC 时间。 */
    private Instant validFrom;

    /**
     * 有效结束 UTC 时间
     * <li>为空表示没有预定结束时间；撤销时为撤销时间。</li>
     */
    private Instant validTo;

    /**
     * 复审频率代码
     * <li>本期不安排持续复审，始终为空。</li>
     */
    private String reviewFrequencyCode;

    /**
     * 下次复审 UTC 时间
     * <li>本期始终为空。</li>
     */
    private Instant nextReviewTime;

    /** 复审状态：APPROVED 为 NOT_REQUIRED，其余为 COMPLETED。 */
    private String reviewStatus;

    /** 并发版本，从 1 开始。 */
    private long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;

    /**
     * 查询时实时判定的资格状态，不含 Client 结算冻结
     * <li>APPROVED 但 valid_to 不晚于 now 为 EXPIRED；valid_from 晚于 now 为 NOT_YET_VALID。</li>
     * <li>未知状态 Code 视为数据损坏，由调用方 Fail-Closed。</li>
     *
     * @param now 查询时间
     * @return 实时状态；未知状态返回 null
     */
    public QualificationEvaluationStatus evaluate(Instant now) {
        QualificationStatus current = QualificationStatus.ofCode(status);
        if (current == null) {
            return null;
        }
        return switch (current) {
            case REJECTED -> QualificationEvaluationStatus.REJECTED;
            case REVOKED -> QualificationEvaluationStatus.REVOKED;
            case EXPIRED -> QualificationEvaluationStatus.EXPIRED;
            case APPROVED -> evaluateApproved(now);
        };
    }

    /** 已批准资格按有效期判定。 */
    QualificationEvaluationStatus evaluateApproved(Instant now) {
        if (validTo != null && !validTo.isAfter(now)) {
            return QualificationEvaluationStatus.EXPIRED;
        }
        if (validFrom.isAfter(now)) {
            return QualificationEvaluationStatus.NOT_YET_VALID;
        }
        return QualificationEvaluationStatus.ELIGIBLE;
    }
}
