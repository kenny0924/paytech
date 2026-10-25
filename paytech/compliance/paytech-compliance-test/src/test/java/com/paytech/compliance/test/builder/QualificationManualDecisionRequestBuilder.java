package com.paytech.compliance.test.builder;

import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.randomId;

import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.core.enums.QualificationType;

/**
 * 手工登记请求 Builder
 * <li>template() 为随机 Client 的 ORGANIZATION_KYB，2026-01-01 起生效、2030-01-01 结束，首次登记不带版本。</li>
 * <li>build() 不修复刻意设置的非法值。</li>
 *
 * @author Kenny
 */
public final class QualificationManualDecisionRequestBuilder {
    public static final String VALID_FROM = "2026-01-01T00:00:00.000000Z";

    public static final String VALID_TO = "2030-01-01T00:00:00.000000Z";

    public static final String REASON_CODE = "MANUAL_VERIFIED";

    public static final String BASIS_NOTE = "Synthetic manual registration basis";

    private final QualificationManualDecisionRequestBody body = new QualificationManualDecisionRequestBody();

    private QualificationManualDecisionRequestBuilder() {
        body.setRoleType(QualificationRoleType.CLIENT.getCode());
        body.setRoleId(Long.toString(randomId()));
        body.setQualificationType(QualificationType.ORGANIZATION_KYB.getCode());
        body.setValidFrom(VALID_FROM);
        body.setValidTo(VALID_TO);
        body.setReasonCode(REASON_CODE);
        body.setBasisNote(BASIS_NOTE);
    }

    /** 合法的首次登记请求。 */
    public static QualificationManualDecisionRequestBuilder template() {
        return new QualificationManualDecisionRequestBuilder();
    }

    /** 某角色与资格类型的合法首次登记请求。 */
    public static QualificationManualDecisionRequestBuilder of(QualificationRoleType roleType, String roleId,
        QualificationType qualificationType) {
        return template().roleType(roleType.getCode()).roleId(roleId).qualificationType(qualificationType.getCode());
    }

    public QualificationManualDecisionRequestBuilder roleType(String value) {
        body.setRoleType(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder roleId(String value) {
        body.setRoleId(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder qualificationType(String value) {
        body.setQualificationType(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder validFrom(String value) {
        body.setValidFrom(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder validTo(String value) {
        body.setValidTo(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder reasonCode(String value) {
        body.setReasonCode(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder basisNote(String value) {
        body.setBasisNote(value);
        return this;
    }

    public QualificationManualDecisionRequestBuilder expectedVersion(String value) {
        body.setExpectedVersion(value);
        return this;
    }

    public QualificationManualDecisionRequestBody build() {
        return body;
    }
}
