package com.paytech.risk.test.mocker.decision;

import static com.paytech.risk.test.builder.decision.RuleSetBuilder.ALLOW_ACTION_VERSION_ID;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.DECISION_POINT_ID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.test.builder.decision.CandidateBuilder;
import com.paytech.risk.test.builder.decision.CandidateBuilder.CompositionFacts;
import com.paytech.risk.test.builder.decision.CandidateBuilder.StaticCheckSpec;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper;
import com.paytech.risk.test.mapper.decision.ReleaseTestMapper.AuditRow;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;

/**
 * TEST Candidate 及其旧发布组成的本地数据 Mocker
 * <li>组成：规则集根、FROZEN 版本与规则闭包、同状态决策点版本、全部输入字段和唯一绑定；ID 与编码随机生成。</li>
 * <li>Candidate：发布根与首期状态链、八项检查审计；发布编号在单例内从随机基数递增，ID 为 15 位随机正整数。</li>
 * <li>Clear 删除本 Mocker 插入的行，并按本 Mocker 的决策点版本回收被测接口新建的发布及审计。</li>
 *
 * @author Kenny
 */
@TestComponent
public class CandidateMocker implements Clear {

    private final RuleSetTestMapper ruleSetMapper;

    private final ReleaseTestMapper mapper;

    private final List<Long> ruleSetIds = new ArrayList<>();

    private final List<Long> versionIds = new ArrayList<>();

    private final List<Long> pointVersionIds = new ArrayList<>();

    private final List<Long> bindingIds = new ArrayList<>();

    private final Set<Long> releaseIds = new LinkedHashSet<>();

    private int nextReleaseNo;

    public CandidateMocker(RuleSetTestMapper ruleSetMapper, ReleaseTestMapper mapper) {
        this.ruleSetMapper = ruleSetMapper;
        this.mapper = mapper;
    }

    /** 已插入的 Candidate 及其完整审计事实。 */
    public record MockedCandidate(CompositionFacts composition, long releaseId, int releaseNo, String contentHash,
        DecisionReleaseStatus status, int version, long createdBy, Instant createTime, Instant updateTime,
        List<AuditRow> stateChanges, List<AuditRow> staticChecks) {
        /** 篡改摘要后的期望事实。 */
        public MockedCandidate withContentHash(String hash) {
            return new MockedCandidate(composition, releaseId, releaseNo, hash, status, version, createdBy, createTime,
                updateTime, stateChanges, staticChecks);
        }

        /** 删除部分检查后的期望事实。 */
        public MockedCandidate withStaticChecks(List<AuditRow> checks) {
            return new MockedCandidate(composition, releaseId, releaseNo, contentHash, status, version, createdBy,
                createTime, updateTime, stateChanges, List.copyOf(checks));
        }
    }

    /** 插入可创建 Candidate 的旧发布组成（或其变体），不插入发布。 */
    public CompositionFacts mockComposition(CandidateBuilder builder) {
        long ruleSetId = randomId();
        String code = "MOCK_RELEASE_RULE_SET_" + ruleSetId;
        String name = "Checkout rules v1";
        long versionId = randomId();
        long ruleId = randomId();
        var rule = builder.ruleProjection();
        String ruleSetHash = CandidateBuilder.ruleSetHash(code, name, 1, rule);
        versionIds.add(versionId);
        ruleSetMapper.insertVersion(versionId, DECISION_POINT_ID, code, name, 1, builder.ruleSetStatus(), ruleSetHash,
            builder.createdBy(), "change 1", builder.createTime().minusSeconds(3600));
        if (builder.isOrderedStringRule()) {
            mapper.insertOrderedStringRule(ruleId, versionId, ALLOW_ACTION_VERSION_ID);
        } else {
            ruleSetMapper.insertMinimalRule(ruleId, versionId, ALLOW_ACTION_VERSION_ID);
        }
        long currentVersionId = builder.hasNewerDraftVersion() ? mockNewerDraft(code) : versionId;
        insertRoot(builder, ruleSetId, code, currentVersionId);

        long pointVersionId = 0;
        int pointVersionNo = 0;
        String pointHash = null;
        long bindingId = 0;
        Map<String, Long> inputFieldIds = new LinkedHashMap<>();
        if (!builder.isWithoutCompanion()) {
            pointVersionId = randomId();
            pointVersionNo = ThreadLocalRandom.current().nextInt(100_000, 2_000_000_000);
            pointHash = CandidateBuilder.pointHash(pointVersionNo, code, 1, ruleSetHash);
            pointVersionIds.add(pointVersionId);
            ruleSetMapper.insertPointVersion(pointVersionId, DECISION_POINT_ID, pointVersionNo, pointHash);
            if (builder.ruleSetStatus() == RuleSetVersionStatus.DRAFT) {
                mapper.updatePointVersionStatus(pointVersionId, DecisionPointVersionStatus.DRAFT);
            }
            for (var field : CandidateBuilder.inputFields()) {
                long inputFieldId = randomId();
                inputFieldIds.put(field.fieldCode(), inputFieldId);
                mapper.insertInputField(inputFieldId, pointVersionId, field);
            }
            bindingId = randomId();
            bindingIds.add(bindingId);
            ruleSetMapper.insertBinding(bindingId, pointVersionId, versionId);
        }
        return new CompositionFacts(ruleSetId, code, name, versionId, 1, builder.ruleSetStatus(), ruleSetHash,
            pointVersionId, pointVersionNo, pointHash, bindingId, ruleId, Map.copyOf(inputFieldIds), rule);
    }

    /** 插入完整组成及其 Candidate 发布根和审计链。 */
    public MockedCandidate mockCandidate(CandidateBuilder builder) {
        return mockCandidate(mockComposition(builder), builder);
    }

    /** 在已插入的组成上再插入一个 Candidate。 */
    public MockedCandidate mockCandidate(CompositionFacts composition, CandidateBuilder builder) {
        long releaseId = randomId();
        String releaseNo = builder.releaseNo() != null ? builder.releaseNo() : Integer.toString(nextReleaseNo());
        int canonicalNo = releaseNo.matches("[1-9][0-9]{0,9}") ? (int) Math.min(Long.parseLong(releaseNo),
            Integer.MAX_VALUE) : 0;
        String contentHash = canonicalNo > 0 ? CandidateBuilder.releaseHash(composition, releaseId, canonicalNo)
            : RuleSetBuilder.sha256(releaseNo);
        List<AuditRow> states = stateChanges(builder, releaseId);
        List<AuditRow> checks = builder.checksCompleted() ? staticChecks(builder, composition, releaseId) : List.of();
        Instant updateTime = states.getLast().eventTime();
        releaseIds.add(releaseId);
        mapper.insertRelease(releaseId, releaseNo, DECISION_POINT_ID, composition.pointVersionId(),
            builder.environment(), builder.status(), contentHash, builder.createdBy(), builder.version(),
            builder.createTime(), updateTime);
        states.forEach(mapper::insertAudit);
        checks.forEach(mapper::insertAudit);
        return new MockedCandidate(composition, releaseId, canonicalNo, contentHash, builder.status(),
            builder.version(), builder.createdBy(), builder.createTime(), updateTime, states, checks);
    }

    /** 篡改发布内容摘要，构造冻结闭包与根不一致；返回篡改后的期望事实。 */
    public MockedCandidate corruptReleaseHash(MockedCandidate candidate) {
        mapper.updateReleaseContentHash(candidate.releaseId(), "0".repeat(64));
        return candidate.withContentHash("0".repeat(64));
    }

    /** 篡改决策点版本内容摘要，构造冻结组成漂移。 */
    public void corruptPointVersionHash(CompositionFacts composition) {
        mapper.updatePointVersionHash(composition.pointVersionId(), "0".repeat(64));
    }

    /** 篡改规则集版本内容摘要，构造规则内容漂移。 */
    public void corruptRuleSetHash(CompositionFacts composition, String contentHash) {
        ruleSetMapper.updateContentHash(composition.ruleSetVersionId(), contentHash);
    }

    /** 删除组成的唯一绑定，构造冻结组成断边。 */
    public void removeBinding(CompositionFacts composition) {
        mapper.deleteBinding(composition.bindingId());
    }

    /** 删除一条已插入的审计事件，构造审计链缺口。 */
    public void removeAudit(AuditRow audit) {
        mapper.deleteAudit(audit.auditEventId());
    }

    /** 规则集版本改为统一变量配置格式。 */
    public void markVariableConfiguration(CompositionFacts composition) {
        mapper.updateContentFormat(composition.ruleSetVersionId(), VariableRuleConfiguration.CONTENT_FORMAT);
    }

    /** 删除本 Mocker 插入或其组成上被新建的全部数据；删除失败时保留记录，下一次清理继续重试。 */
    @Override
    public void clear() {
        Set<Long> releases = new LinkedHashSet<>(releaseIds);
        pointVersionIds.forEach(id -> releases.addAll(mapper.findReleaseIds(id)));
        mapper.deleteReleases(releases);
        mapper.deleteInputFields(pointVersionIds);
        ruleSetMapper.deleteAll(List.of(), bindingIds, pointVersionIds, versionIds, ruleSetIds);
        releaseIds.clear();
        bindingIds.clear();
        pointVersionIds.clear();
        versionIds.clear();
        ruleSetIds.clear();
        nextReleaseNo = 0;
    }

    private void insertRoot(CandidateBuilder builder, long ruleSetId, String code, long currentVersionId) {
        ruleSetIds.add(ruleSetId);
        if (builder.isDeletedRoot()) {
            ruleSetMapper.insertDeletedRuleSet(ruleSetId, DECISION_POINT_ID, code, currentVersionId,
                CandidateBuilder.ACTOR_ID, RuleSetBuilder.sha256("RISK_RULE_SET_DELETE:" + ruleSetId),
                builder.createTime());
        } else {
            ruleSetMapper.insertRuleSet(ruleSetId, DECISION_POINT_ID, code, currentVersionId, builder.createTime());
        }
    }

    /** 同一编码下的 v2 DRAFT 当前版本，不带旧发布组成。 */
    private long mockNewerDraft(String code) {
        long versionId = randomId();
        String name = "Checkout rules v2";
        versionIds.add(versionId);
        ruleSetMapper.insertVersion(versionId, DECISION_POINT_ID, code, name, 2, RuleSetVersionStatus.DRAFT,
            RuleSetBuilder.contentHash(code, name, 2), CandidateBuilder.ACTOR_ID, "change 2",
            CandidateBuilder.CREATE_TIME.minusSeconds(1800));
        ruleSetMapper.insertMinimalRule(randomId(), versionId, ALLOW_ACTION_VERSION_ID);
        return versionId;
    }

    /** 首期状态链：创建、开始校验、校验失败。 */
    private static List<AuditRow> stateChanges(CandidateBuilder builder, long releaseId) {
        String createTrigger = RuleSetBuilder.sha256("RISK_TEST_RELEASE:" + releaseId + ":CREATE");
        String validateTrigger = RuleSetBuilder.sha256("RISK_TEST_RELEASE:" + releaseId + ":VALIDATE");
        List<AuditRow> rows = new ArrayList<>();
        rows.add(stateChange(releaseId, builder.createTime(), null, DecisionReleaseStatus.DRAFT, "CANDIDATE_CREATED",
            createTrigger, builder.createdBy(), 1));
        if (builder.stateChangeCount() >= 2) {
            rows.add(stateChange(releaseId, builder.startTime(), DecisionReleaseStatus.DRAFT,
                DecisionReleaseStatus.VALIDATING, "STATIC_VALIDATION_STARTED", validateTrigger, builder.validatedBy(),
                2));
        }
        if (builder.stateChangeCount() == 3) {
            rows.add(stateChange(releaseId, builder.completeTime(), DecisionReleaseStatus.VALIDATING,
                DecisionReleaseStatus.REJECTED, "STATIC_VALIDATION_FAILED", validateTrigger, builder.validatedBy(),
                3));
        }
        return List.copyOf(rows);
    }

    private static AuditRow stateChange(long releaseId, Instant time, DecisionReleaseStatus from,
        DecisionReleaseStatus to, String reasonCode, String triggerId, long actorId, int aggregateVersion) {
        return new AuditRow(randomId(), releaseId, ReleaseAuditEventType.STATE_CHANGE, time, null, null, null, null,
            null, from, to, reasonCode, "OPERATIONS_COMMAND", triggerId, actorId, aggregateVersion, time);
    }

    private static List<AuditRow> staticChecks(CandidateBuilder builder, CompositionFacts composition,
        long releaseId) {
        Instant time = builder.completeTime();
        List<AuditRow> rows = new ArrayList<>();
        for (StaticCheckSpec check : builder.checks(composition.ruleId())) {
            rows.add(new AuditRow(randomId(), releaseId, ReleaseAuditEventType.STATIC_CHECK, time, check.checkCode(),
                check.checkStatus(), check.severity(), check.errorCode(), check.errorSummary(), null, null, null,
                null, null, null, null, time));
        }
        return List.copyOf(rows);
    }

    /** 首次使用时取随机基数，此后递增；不同用例互不依赖具体编号。 */
    private int nextReleaseNo() {
        if (nextReleaseNo == 0) {
            nextReleaseNo = ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
        }
        return nextReleaseNo++;
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
