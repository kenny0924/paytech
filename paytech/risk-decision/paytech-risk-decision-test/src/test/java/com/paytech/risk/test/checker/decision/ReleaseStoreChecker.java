package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.test.builder.decision.CandidateBuilder.CompositionFacts;
import com.paytech.risk.test.builder.decision.CandidateBuilder.StaticCheckSpec;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper.AuditRow;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper.ReleaseRow;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

/**
 * 发布根与审计事件的数据库 Checker
 * <li>只读真实数据库；期望值来自 Candidate 夹具与首期状态合同（触发标识为发布 ID 与阶段的 SHA-256）。</li>
 * <li>拒绝场景核对根与审计逐字段保持原样，创建拒绝核对组成上没有新发布。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ReleaseStoreChecker {

    private final ReleaseTestMapper mapper;

    public ReleaseStoreChecker(ReleaseTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 根与全部审计事件保持夹具原样。 */
    public void assertUnchanged(MockedCandidate candidate) {
        ReleaseRow row = requireRelease(candidate.releaseId());
        assertAll("Candidate " + candidate.releaseId() + " 不应变化",
            () -> assertEquals(candidate.status().getCode(), row.status()),
            () -> assertEquals(candidate.version(), row.version()),
            () -> assertEquals(candidate.contentHash(), row.contentHash()),
            () -> assertEquals(candidate.updateTime(), row.updateTime()),
            () -> assertEquals(candidate.stateChanges(), mapper.findStateChanges(candidate.releaseId())),
            () -> assertEquals(candidate.staticChecks(), mapper.findStaticChecks(candidate.releaseId())),
            () -> assertEquals(candidate.stateChanges().size() + candidate.staticChecks().size(),
                mapper.countAudits(candidate.releaseId())));
    }

    /** 组成上只有给定的发布，没有被新建 Candidate。 */
    public void assertReleases(CompositionFacts composition, long... releaseIds) {
        List<Long> expected = new ArrayList<>();
        for (long id : releaseIds) {
            expected.add(id);
        }
        assertEquals(expected.stream().sorted().toList(), mapper.findReleaseIds(composition.pointVersionId()),
            "组成上的发布集合不符");
    }

    /** 新建 Candidate 的持久化根与初始事件。 */
    public record Created(ReleaseRow release, AuditRow initial) {
    }

    /**
     * 新建的 DRAFT/version=1 Candidate 根与唯一初始事件。
     *
     * @return 持久化事实，供响应比对
     */
    public Created assertCreated(long releaseId, CompositionFacts composition, int releaseNo, long actorId,
        Instant notBefore, Instant notAfter) {
        ReleaseRow row = requireRelease(releaseId);
        List<AuditRow> states = mapper.findStateChanges(releaseId);
        assertEquals(1, states.size(), "新 Candidate 应只有初始事件");
        assertTrue(mapper.findStaticChecks(releaseId).isEmpty(), "新 Candidate 不应有检查结果");
        AuditRow initial = states.getFirst();
        assertAll("新 Candidate " + releaseId,
            () -> assertEquals(Integer.toString(releaseNo), row.releaseNo()),
            () -> assertEquals(RuleSetBuilder.DECISION_POINT_ID, row.decisionPointId()),
            () -> assertEquals(composition.pointVersionId(), row.pointVersionId()),
            () -> assertEquals(DecisionEnvironment.TEST.getCode(), row.environmentCode()),
            () -> assertEquals(DecisionReleaseStatus.DRAFT.getCode(), row.status()),
            () -> assertNull(row.activeSlot(), "Candidate 不占用生效槽"),
            () -> assertTrue(row.contentHash().matches("[0-9a-f]{64}"), "内容摘要格式"),
            () -> assertEquals(actorId, row.createdBy()),
            () -> assertNull(row.activatedTime()),
            () -> assertNull(row.supersededTime()),
            () -> assertEquals(1, row.version()),
            () -> assertFalse(row.createTime().isBefore(notBefore), "创建时间早于请求"),
            () -> assertFalse(row.createTime().isAfter(notAfter), "创建时间晚于响应"),
            () -> assertEquals(row.createTime(), row.updateTime()),
            () -> assertStateChange(initial, releaseId, row.createTime(), null, DecisionReleaseStatus.DRAFT,
                "CANDIDATE_CREATED", "CREATE", actorId, 1));
        return new Created(row, initial);
    }

    /**
     * 首次校验完成后的根与追加事实：开始事件、八项检查及失败时的拒绝事件，原审计保留。
     *
     * @return 持久化的八项检查，供响应比对
     */
    public List<AuditRow> assertValidated(MockedCandidate original, DecisionReleaseStatus status, int version,
        List<StaticCheckSpec> checks, long actorId, Instant notBefore) {
        ReleaseRow row = requireRelease(original.releaseId());
        List<AuditRow> states = mapper.findStateChanges(original.releaseId());
        List<AuditRow> stored = mapper.findStaticChecks(original.releaseId());
        int expectedStates = status == DecisionReleaseStatus.REJECTED ? 3 : 2;
        assertEquals(expectedStates, states.size(), "状态事件数量");
        assertEquals(checks.size(), stored.size(), "检查数量");
        assertEquals(expectedStates + checks.size(), mapper.countAudits(original.releaseId()), "审计总数");
        AuditRow started = states.get(1);
        Instant completed = stored.getFirst().eventTime();
        assertAll("校验后的 Candidate " + original.releaseId(),
            () -> assertEquals(status.getCode(), row.status()),
            () -> assertEquals(version, row.version()),
            () -> assertEquals(original.contentHash(), row.contentHash()),
            () -> assertEquals(original.createTime(), row.createTime()),
            () -> assertEquals(original.stateChanges(), states.subList(0, original.stateChanges().size()),
                "原状态事件应保持不变"),
            () -> assertStateChange(started, original.releaseId(), started.eventTime(), DecisionReleaseStatus.DRAFT,
                DecisionReleaseStatus.VALIDATING, "STATIC_VALIDATION_STARTED", "VALIDATE", actorId, 2),
            () -> assertFalse(started.eventTime().isBefore(original.status() == DecisionReleaseStatus.DRAFT
                ? notBefore : original.createTime()), "开始事件时间"),
            () -> assertFalse(completed.isBefore(started.eventTime()), "检查时间早于开始事件"),
            () -> assertFalse(completed.isBefore(notBefore), "检查时间早于请求"),
            () -> assertEquals(status == DecisionReleaseStatus.REJECTED ? completed : started.eventTime(),
                row.updateTime(), "根更新时间"));
        for (int i = 0; i < checks.size(); i++) {
            StaticCheckSpec spec = checks.get(i);
            AuditRow actual = stored.get(i);
            assertAll("检查 " + spec.checkCode(),
                () -> assertEquals(ReleaseAuditEventType.STATIC_CHECK, actual.eventType()),
                () -> assertEquals(spec.checkCode(), actual.checkCode()),
                () -> assertEquals(spec.checkStatus(), actual.checkStatus()),
                () -> assertEquals(spec.severity(), actual.severity()),
                () -> assertEquals(spec.errorCode(), actual.errorCode()),
                () -> assertEquals(spec.errorSummary(), actual.errorSummary()),
                () -> assertEquals(completed, actual.eventTime(), "八项检查同一时刻写入"),
                () -> assertEquals(actual.eventTime(), actual.createTime()),
                () -> assertNull(actual.fromStatus()),
                () -> assertNull(actual.toStatus()),
                () -> assertNull(actual.reasonCode()),
                () -> assertNull(actual.triggerType()),
                () -> assertNull(actual.triggerId()),
                () -> assertNull(actual.actorId()),
                () -> assertNull(actual.aggregateVersion()));
        }
        if (status == DecisionReleaseStatus.REJECTED) {
            assertStateChange(states.get(2), original.releaseId(), completed, DecisionReleaseStatus.VALIDATING,
                DecisionReleaseStatus.REJECTED, "STATIC_VALIDATION_FAILED", "VALIDATE", actorId, 3);
        }
        return stored;
    }

    private ReleaseRow requireRelease(long releaseId) {
        ReleaseRow row = mapper.findRelease(releaseId);
        assertNotNull(row, "发布不存在：" + releaseId);
        return row;
    }

    private static void assertStateChange(AuditRow actual, long releaseId, Instant time, DecisionReleaseStatus from,
        DecisionReleaseStatus to, String reasonCode, String stage, long actorId, int aggregateVersion) {
        assertAll("状态事件 v" + aggregateVersion,
            () -> assertEquals(releaseId, actual.releaseId()),
            () -> assertEquals(ReleaseAuditEventType.STATE_CHANGE, actual.eventType()),
            () -> assertEquals(time, actual.eventTime()),
            () -> assertEquals(time, actual.createTime()),
            () -> assertNull(actual.checkCode()),
            () -> assertNull(actual.checkStatus()),
            () -> assertNull(actual.severity()),
            () -> assertNull(actual.errorCode()),
            () -> assertNull(actual.errorSummary()),
            () -> assertEquals(from, actual.fromStatus()),
            () -> assertEquals(to, actual.toStatus()),
            () -> assertEquals(reasonCode, actual.reasonCode()),
            () -> assertEquals("OPERATIONS_COMMAND", actual.triggerType()),
            () -> assertEquals(RuleSetBuilder.sha256("RISK_TEST_RELEASE:" + releaseId + ":" + stage),
                actual.triggerId()),
            () -> assertEquals(actorId, actual.actorId()),
            () -> assertEquals(aggregateVersion, actual.aggregateVersion()));
    }
}
