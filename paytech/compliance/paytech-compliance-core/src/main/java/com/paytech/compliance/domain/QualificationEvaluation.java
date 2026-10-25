package com.paytech.compliance.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.paytech.compliance.core.dal.dataobject.RoleQualification;
import com.paytech.compliance.core.enums.QualificationEvaluationStatus;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationType;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一个主体在查询时间的资格判定：选中的资格行（可空）与实时状态
 * <li>只看该角色要求的资格类型；CLIENT 有多类资格时优先取合格的一行，否则取最近更新的一行。</li>
 * <li>Client 的结算冻结只在资格本身合格时判定，冻结结果由 {@link #withSettlementBlocks(Set)} 叠加。</li>
 */
@Getter
@AllArgsConstructor
public class QualificationEvaluation {
    /** 合格优先，其次最近更新、资格 ID 较大者。 */
    private static final Comparator<Selected> PREFERENCE = Comparator
        .comparing((Selected item) -> item.status() != QualificationEvaluationStatus.ELIGIBLE)
        .thenComparing(item -> item.row().getUpdateTime(), Comparator.reverseOrder())
        .thenComparing(item -> item.row().getQualificationId(), Comparator.reverseOrder());

    /** 被判定的主体。 */
    private final QualificationSubject subject;
    /** 选中的资格行；未登记时为空。 */
    private final RoleQualification qualification;
    /** 实时状态。 */
    private final QualificationEvaluationStatus status;

    /**
     * 从该主体的全部资格行中判定
     *
     * @param subject 主体
     * @param rows 该主体的全部资格行
     * @param now 查询时间
     * @return 判定结果；没有要求类型的资格行时为 NOT_REGISTERED
     */
    public static QualificationEvaluation of(QualificationSubject subject, List<RoleQualification> rows, Instant now) {
        Set<QualificationType> required = subject.getRoleType().getQualificationTypes();
        return rows.stream().filter(row -> required.contains(QualificationType.ofCode(row.getQualificationType())))
            .map(row -> new Selected(row, statusOf(row, now))).min(PREFERENCE)
            .map(item -> new QualificationEvaluation(subject, item.row(), item.status()))
            .orElseGet(() -> new QualificationEvaluation(subject, null, QualificationEvaluationStatus.NOT_REGISTERED));
    }

    /**
     * 单行资格的实时状态，叠加 Client 结算冻结
     *
     * @param row 资格行
     * @param now 查询时间
     * @param blockedClientIds 生效中结算冻结的 Client ID
     * @return 实时状态
     */
    public static QualificationEvaluationStatus effectiveStatus(RoleQualification row, Instant now, Set<Long> blockedClientIds) {
        QualificationEvaluationStatus status = statusOf(row, now);
        if (status == QualificationEvaluationStatus.ELIGIBLE && isClient(row)
            && blockedClientIds.contains(Long.parseLong(row.getRoleId()))) {
            return QualificationEvaluationStatus.SETTLEMENT_BLOCKED;
        }
        return status;
    }

    /** 资格合格的 Client 需要再检查结算冻结；返回其 Client ID，其他主体返回空。 */
    public Long clientIdToCheck() {
        if (status != QualificationEvaluationStatus.ELIGIBLE || subject.getRoleType() != QualificationRoleType.CLIENT) {
            return null;
        }
        return Long.parseLong(subject.getRoleId());
    }

    /** 资格行若是合格的 Client，返回其 Client ID 以检查结算冻结，否则返回空。 */
    public static Long clientIdToCheck(RoleQualification row, Instant now) {
        return isClient(row) && statusOf(row, now) == QualificationEvaluationStatus.ELIGIBLE ? Long.parseLong(row.getRoleId()) : null;
    }

    /** 叠加结算冻结：合格的 Client 在冻结集合中时改为 SETTLEMENT_BLOCKED。 */
    public QualificationEvaluation withSettlementBlocks(Set<Long> blockedClientIds) {
        Long clientId = clientIdToCheck();
        if (clientId == null || !blockedClientIds.contains(clientId)) {
            return this;
        }
        return new QualificationEvaluation(subject, qualification, QualificationEvaluationStatus.SETTLEMENT_BLOCKED);
    }

    /** 状态 Code 未知说明数据损坏，按系统错误 Fail-Closed。 */
    static QualificationEvaluationStatus statusOf(RoleQualification row, Instant now) {
        QualificationEvaluationStatus status = row.evaluate(now);
        if (status == null) {
            throw new ErrorException("角色资格状态未知: " + row.getQualificationId());
        }
        return status;
    }

    /** 资格行是否属于 Client。 */
    static boolean isClient(RoleQualification row) {
        return QualificationRoleType.CLIENT.getCode().equals(row.getRoleType());
    }

    /** 候选资格行及其实时状态。 */
    record Selected(RoleQualification row, QualificationEvaluationStatus status) {
    }
}
