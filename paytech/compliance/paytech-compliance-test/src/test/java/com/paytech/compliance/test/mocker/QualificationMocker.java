package com.paytech.compliance.test.mocker;

import static com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder.randomId;

import java.time.Instant;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.compliance.core.enums.QualificationDecisionSource;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.test.builder.RoleQualificationBuilder;
import com.paytech.compliance.test.mapper.QualificationTestMapper;
import com.paytech.compliance.test.mapper.QualificationTestMapper.DecisionRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.QualificationRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.StateChangeRow;

/**
 * 角色资格与 Client 结算冻结的本地前置数据
 * <li>每条资格插入当前决定、投影与一条聚合版本等于投影版本的状态变更；不保存跨用例状态，数据由 ComplianceDataCleaner 统一清理。</li>
 *
 * @author Kenny
 */
@TestComponent
public class QualificationMocker {
    /** 前置状态变更的触发类型。 */
    public static final String FIXTURE_TRIGGER = "TEST_FIXTURE";

    /** 前置决定的决定人。 */
    public static final long FIXTURE_ACTOR = 930000009L;

    private final QualificationTestMapper mapper;

    public QualificationMocker(QualificationTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 已插入的资格、当前决定与状态变更。 */
    public record MockedQualification(QualificationRow qualification, DecisionRow decision, StateChangeRow stateChange) {
        public long qualificationId() {
            return qualification.qualificationId();
        }

        public long version() {
            return qualification.version();
        }
    }

    /** 按 Builder 插入一条资格。 */
    public MockedQualification mock(RoleQualificationBuilder builder) {
        long decisionId = randomId();
        long qualificationId = randomId();
        boolean manual = builder.decisionSource() == QualificationDecisionSource.MANUAL;
        String status = builder.status().getCode();
        DecisionRow decision = new DecisionRow(decisionId, null, null, builder.roleType().getCode(), builder.roleId(),
            builder.qualificationType().getCode(), builder.decisionSource().getCode(), status,
            manual ? RoleQualificationBuilder.REASON_CODE : null, manual ? RoleQualificationBuilder.BASIS_NOTE : null,
            builder.validFrom(), builder.validTo(), FIXTURE_ACTOR, builder.updateTime());
        QualificationRow qualification = new QualificationRow(qualificationId, builder.roleType().getCode(), builder.roleId(),
            builder.qualificationType().getCode(), decisionId, status, builder.validFrom(), builder.validTo(), null, null,
            builder.status().reviewStatus().getCode(), builder.version(), builder.updateTime());
        StateChangeRow change = new StateChangeRow(randomId(), qualificationId,
            builder.version() == 1 ? null : QualificationStatus.APPROVED.getCode(), status, decisionId, null, FIXTURE_TRIGGER,
            Long.toString(decisionId), FIXTURE_ACTOR, builder.version(), builder.updateTime());
        mapper.insertDecision(decision);
        mapper.insertQualification(qualification);
        mapper.insertStateChange(change);
        return new MockedQualification(qualification, decision, change);
    }

    /** 为 Client 插入一条结算控制根行。 */
    public long mockSettlementControl(String clientId, String controlType, String status, Instant effectiveFrom,
        Instant effectiveTo) {
        long controlId = randomId();
        mapper.insertSettlementControl(controlId, "CLIENT", Long.parseLong(clientId), controlType, status, effectiveFrom,
            effectiveTo);
        return controlId;
    }
}
