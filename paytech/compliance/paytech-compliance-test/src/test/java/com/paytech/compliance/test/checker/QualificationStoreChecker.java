package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.compliance.core.enums.QualificationReviewStatus;
import com.paytech.compliance.core.enums.QualificationStatus;
import com.paytech.compliance.test.mapper.QualificationTestMapper;
import com.paytech.compliance.test.mapper.QualificationTestMapper.DecisionRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.QualificationRow;
import com.paytech.compliance.test.mapper.QualificationTestMapper.StateChangeRow;
import com.paytech.compliance.test.mocker.QualificationMocker.MockedQualification;

/**
 * 角色资格落库事实的只读 Checker：投影、当前决定与最新状态变更三者一致
 *
 * @author Kenny
 */
@TestComponent
public class QualificationStoreChecker {
    private final QualificationTestMapper mapper;

    public QualificationStoreChecker(QualificationTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 一次资格变更的期望；validFrom 为空表示取本次决定时间（入驻审核结论）。 */
    public record ExpectedChange(String roleType, String roleId, String qualificationType, QualificationStatus status,
        String decisionSource, Long caseId, Instant validFrom, Instant validTo, String reasonCode, String basisNote,
        Long actorId, String triggerType, QualificationStatus fromStatus, long version, Instant startedAt) {
    }

    /**
     * 角色恰有一条资格，且投影、当前决定与最新状态变更符合期望
     *
     * @param expected 期望
     * @return 当前投影行
     */
    public QualificationRow assertChanged(ExpectedChange expected) {
        List<QualificationRow> rows = mapper.qualificationsOf(expected.roleType(), expected.roleId());
        assertEquals(1, rows.size(), "角色资格行数");
        QualificationRow row = rows.getFirst();
        DecisionRow decision = mapper.findDecision(row.currentDecisionId());
        assertNotNull(decision, "当前决定");
        List<StateChangeRow> changes = mapper.stateChanges(row.qualificationId());
        StateChangeRow change = changes.getLast();
        Instant now = decision.decidedTime();
        Instant validTo = expected.status() == QualificationStatus.REVOKED ? now : expected.validTo();
        Instant validFrom = expected.validFrom() == null ? now : expected.validFrom();
        assertTrue(!now.isBefore(expected.startedAt().truncatedTo(ChronoUnit.MICROS)) && !now.isAfter(Instant.now()),
            "决定时间应为本次命令时间：" + now);
        assertAll("资格投影", () -> assertEquals(expected.qualificationType(), row.qualificationType()),
            () -> assertEquals(expected.status().getCode(), row.status()),
            () -> assertEquals(validFrom, row.validFrom()), () -> assertEquals(validTo, row.validTo()),
            () -> assertNull(row.reviewFrequencyCode()), () -> assertNull(row.nextReviewTime()),
            () -> assertEquals(reviewStatus(expected.status()).getCode(), row.reviewStatus()),
            () -> assertEquals(expected.version(), row.version()), () -> assertEquals(now, row.updateTime()));
        assertAll("当前决定", () -> assertEquals(expected.caseId(), decision.caseId()), () -> assertNull(decision.decisionVersion()),
            () -> assertEquals(expected.roleType(), decision.roleType()), () -> assertEquals(expected.roleId(), decision.roleId()),
            () -> assertEquals(expected.qualificationType(), decision.qualificationType()),
            () -> assertEquals(expected.decisionSource(), decision.decisionSource()),
            () -> assertEquals(expected.status().getCode(), decision.outcome()),
            () -> assertEquals(validFrom, decision.validFrom()), () -> assertEquals(validTo, decision.validTo()),
            () -> assertEquals(expected.caseId() == null ? expected.reasonCode() : null, decision.overrideReasonCode()),
            () -> assertEquals(expected.basisNote(), decision.basisNote()),
            () -> assertEquals(expected.actorId(), decision.decidedBy()));
        assertAll("状态变更", () -> assertEquals(expected.fromStatus() == null ? null : expected.fromStatus().getCode(), change.fromStatus()),
            () -> assertEquals(expected.status().getCode(), change.toStatus()),
            () -> assertEquals(decision.decisionId(), change.decisionId()),
            () -> assertEquals(expected.reasonCode(), change.reasonCode()),
            () -> assertEquals(expected.triggerType(), change.triggerType()),
            () -> assertEquals(Long.toString(decision.decisionId()), change.triggerId()),
            () -> assertEquals(expected.actorId(), change.actorId()),
            () -> assertEquals(expected.version(), change.aggregateVersion()), () -> assertEquals(now, change.occurredTime()));
        return row;
    }

    /**
     * 前置资格保持原样：投影与状态变更都没有变化，当前决定仍是前置决定
     * <li>入驻审核结论本身会写案件决定，因此不检查该角色的决定总数；被拒绝的写命令另以 rowCount 断言零写入。</li>
     */
    public void assertUnchanged(MockedQualification mocked) {
        QualificationRow row = mapper.findQualification(mocked.qualificationId());
        assertAll("资格保持原样", () -> assertEquals(mocked.qualification(), row, "资格投影不应变化"),
            () -> assertEquals(mocked.decision(), mapper.findDecision(mocked.decision().decisionId()), "前置决定不应变化"),
            () -> assertEquals(List.of(mocked.stateChange()), mapper.stateChanges(mocked.qualificationId()), "不应新增状态变更"));
    }

    /** 某角色没有角色资格投影。 */
    public void assertNoQualification(String roleType, String roleId) {
        assertTrue(mapper.qualificationsOf(roleType, roleId).isEmpty(), "不应形成资格：" + roleType + "/" + roleId);
    }

    /** 读取库中投影。 */
    public QualificationRow qualification(long qualificationId) {
        QualificationRow row = mapper.findQualification(qualificationId);
        assertNotNull(row, "资格不存在：" + qualificationId);
        return row;
    }

    /** 某资格全部状态变更，按版本升序。 */
    public List<StateChangeRow> stateChanges(long qualificationId) {
        return mapper.stateChanges(qualificationId);
    }

    /** 读取决定。 */
    public DecisionRow decision(long decisionId) {
        DecisionRow row = mapper.findDecision(decisionId);
        assertNotNull(row, "决定不存在：" + decisionId);
        return row;
    }

    /** 三张资格表总行数。 */
    public long rowCount() {
        return mapper.countQualificationRows();
    }

    static QualificationReviewStatus reviewStatus(QualificationStatus status) {
        return status.reviewStatus();
    }
}
