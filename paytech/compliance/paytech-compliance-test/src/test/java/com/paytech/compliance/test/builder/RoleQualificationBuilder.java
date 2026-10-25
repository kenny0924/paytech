package com.paytech.compliance.test.builder;

import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.randomId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.core.enums.QualificationType;

/**
 * 角色资格前置数据的 Builder：一条当前决定、一条投影与一条状态变更
 * <li>template() 为一个随机 Client 的 ORGANIZATION_KYB 已批准资格，前一天生效、没有预定结束时间，版本 1，来源 MANUAL。</li>
 * <li>build 不修复刻意设置的组合；复审状态随状态推导（APPROVED 为 NOT_REQUIRED，其余为 COMPLETED）。</li>
 *
 * @author Kenny
 */
public final class RoleQualificationBuilder {
    /** 手工登记原因代码。 */
    public static final String REASON_CODE = "MANUAL_VERIFIED";

    /** 手工登记依据说明。 */
    public static final String BASIS_NOTE = "Synthetic manual qualification basis";

    QualificationRoleType roleType = QualificationRoleType.CLIENT;

    String roleId = Long.toString(randomId());

    QualificationType qualificationType = QualificationType.ORGANIZATION_KYB;

    QualificationStatus status = QualificationStatus.APPROVED;

    QualificationDecisionSource decisionSource = QualificationDecisionSource.MANUAL;

    Instant validFrom = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.DAYS);

    Instant validTo;

    long version = 1;

    Instant updateTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);

    private RoleQualificationBuilder() {
    }

    /** 一个随机 Client 的已批准 ORGANIZATION_KYB 资格。 */
    public static RoleQualificationBuilder template() {
        return new RoleQualificationBuilder();
    }

    /** 某角色的已批准资格。 */
    public static RoleQualificationBuilder of(QualificationRoleType roleType, String roleId, QualificationType qualificationType) {
        return template().roleType(roleType).roleId(roleId).qualificationType(qualificationType);
    }

    public RoleQualificationBuilder roleType(QualificationRoleType value) {
        roleType = value;
        return this;
    }

    public RoleQualificationBuilder roleId(String value) {
        roleId = value;
        return this;
    }

    public RoleQualificationBuilder qualificationType(QualificationType value) {
        qualificationType = value;
        return this;
    }

    public RoleQualificationBuilder status(QualificationStatus value) {
        status = value;
        return this;
    }

    public RoleQualificationBuilder decisionSource(QualificationDecisionSource value) {
        decisionSource = value;
        return this;
    }

    public RoleQualificationBuilder validFrom(Instant value) {
        validFrom = value;
        return this;
    }

    public RoleQualificationBuilder validTo(Instant value) {
        validTo = value;
        return this;
    }

    public RoleQualificationBuilder version(long value) {
        version = value;
        return this;
    }

    public RoleQualificationBuilder updateTime(Instant value) {
        updateTime = value;
        return this;
    }

    public QualificationRoleType roleType() {
        return roleType;
    }

    public String roleId() {
        return roleId;
    }

    public QualificationType qualificationType() {
        return qualificationType;
    }

    public QualificationStatus status() {
        return status;
    }

    public QualificationDecisionSource decisionSource() {
        return decisionSource;
    }

    public Instant validFrom() {
        return validFrom;
    }

    public Instant validTo() {
        return validTo;
    }

    public long version() {
        return version;
    }

    public Instant updateTime() {
        return updateTime;
    }
}
