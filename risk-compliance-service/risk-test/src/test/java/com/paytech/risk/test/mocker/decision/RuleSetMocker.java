package com.paytech.risk.test.mocker.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder.VersionSpec;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;

/**
 * 规则集及发布引用的本地数据 Mocker
 * <li>每个版本插入 {@link RuleSetBuilder#MINIMAL_RULE} 对应的规则闭包。ID 与编码随机生成；发布 ID 在单例内递增，使发布 ID 倒序可预期。</li>
 * <li>每个被发布的规则集版本复用一个冻结决策点版本及唯一绑定；Clear 只删除本 Mocker 插入的行。</li>
 *
 * @author Kenny
 */
@TestComponent
public class RuleSetMocker implements Clear {

    private final RuleSetTestMapper mapper;

    private final List<Long> ruleSetIds = new ArrayList<>();

    private final List<Long> versionIds = new ArrayList<>();

    private final List<Long> pointVersionIds = new ArrayList<>();

    private final List<Long> bindingIds = new ArrayList<>();

    private final List<Long> releaseIds = new ArrayList<>();

    /** 规则集版本 ID 到其发布所用决策点版本。 */
    private final Map<Long, PointVersion> pointVersions = new HashMap<>();

    private long nextReleaseId;

    public RuleSetMocker(RuleSetTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 已插入的规则集版本。 */
    public record MockedVersion(long ruleSetVersionId, int versionNo, VersionSpec spec) {
    }

    /** 已插入的规则集根及其版本，最后一个版本为当前版本。 */
    public record MockedRuleSet(long ruleSetId, String ruleSetCode, List<MockedVersion> versions) {
        /** 按业务版本号取版本。 */
        public MockedVersion version(int versionNo) {
            return versions.get(versionNo - 1);
        }

        public long currentVersionId() {
            return versions.getLast().ruleSetVersionId();
        }
    }

    /** 已插入的发布引用。 */
    public record MockedRelease(long releaseId, String releaseNo, DecisionEnvironment environment,
        DecisionReleaseStatus status,
        long pointVersionId, int pointVersionNo, long ruleSetVersionId, int versionNo, Instant activatedTime,
        Instant supersededTime) {
    }

    private record PointVersion(long pointVersionId, int versionNo) {
    }

    /** 插入规则集根及全部版本。 */
    public MockedRuleSet mock(RuleSetBuilder builder) {
        long ruleSetId = randomId();
        String code = "MOCK_RULE_SET_" + ruleSetId;
        List<MockedVersion> versions = new ArrayList<>();
        for (VersionSpec spec : builder.versionSpecs()) {
            int versionNo = versions.size() + 1;
            long versionId = randomId();
            versionIds.add(versionId);
            mapper.insertVersion(versionId, RuleSetBuilder.DECISION_POINT_ID, code, spec.ruleSetName(), versionNo,
                spec.status(), RuleSetBuilder.contentHash(code, spec.ruleSetName(), versionNo), spec.createdBy(),
                spec.changeReason(), spec.createTime());
            mapper.insertMinimalRule(randomId(), versionId, RuleSetBuilder.ALLOW_ACTION_VERSION_ID);
            versions.add(new MockedVersion(versionId, versionNo, spec));
        }
        long currentVersionId = versions.getLast().ruleSetVersionId();
        Instant time = versions.getLast().spec().createTime();
        ruleSetIds.add(ruleSetId);
        if (builder.isDeleted()) {
            mapper.insertDeletedRuleSet(ruleSetId, RuleSetBuilder.DECISION_POINT_ID, code, currentVersionId, 70001L,
                RuleSetBuilder.sha256("RISK_RULE_SET_DELETE:" + ruleSetId), time);
        } else {
            mapper.insertRuleSet(ruleSetId, RuleSetBuilder.DECISION_POINT_ID, code, currentVersionId, time);
        }
        return new MockedRuleSet(ruleSetId, code, List.copyOf(versions));
    }

    /** 为已冻结的规则集版本插入一条发布；同一版本的发布共用一个冻结决策点版本。 */
    public MockedRelease mockRelease(MockedRuleSet ruleSet, int versionNo, ReleaseBuilder builder) {
        MockedVersion version = ruleSet.version(versionNo);
        PointVersion pointVersion = pointVersions.computeIfAbsent(version.ruleSetVersionId(), this::bindPointVersion);
        long releaseId = nextReleaseId();
        String releaseNo = "MOCK-REL-" + releaseId;
        releaseIds.add(releaseId);
        mapper.insertRelease(releaseId, releaseNo, RuleSetBuilder.DECISION_POINT_ID, pointVersion.pointVersionId(),
            builder.environment(), builder.status(), builder.activeSlot(), RuleSetBuilder.sha256(releaseNo),
            builder.activatedTime(), builder.supersededTime());
        return new MockedRelease(releaseId, releaseNo, builder.environment(), builder.status(),
            pointVersion.pointVersionId(), pointVersion.versionNo(), version.ruleSetVersionId(), versionNo,
            builder.activatedTime(), builder.supersededTime());
    }

    /** 在同一决策点插入指向不存在决策点版本的发布，构造发布闭包断边。 */
    public void mockBrokenRelease() {
        long releaseId = nextReleaseId();
        releaseIds.add(releaseId);
        mapper.insertRelease(releaseId, "MOCK-REL-" + releaseId, RuleSetBuilder.DECISION_POINT_ID, randomId(),
            DecisionEnvironment.TEST, DecisionReleaseStatus.DRAFT, null, RuleSetBuilder.sha256("broken" + releaseId), null, null);
    }

    /** 篡改版本内容摘要，构造不可变内容损坏。 */
    public void corruptContentHash(MockedVersion version) {
        mapper.updateContentHash(version.ruleSetVersionId(), "0".repeat(64));
    }

    /** 删除本 Mocker 插入的全部数据；删除失败时保留记录，下一次清理继续重试。 */
    @Override
    public void clear() {
        mapper.deleteAll(releaseIds, bindingIds, pointVersionIds, versionIds, ruleSetIds);
        releaseIds.clear();
        bindingIds.clear();
        pointVersionIds.clear();
        versionIds.clear();
        ruleSetIds.clear();
        pointVersions.clear();
        nextReleaseId = 0;
    }

    private PointVersion bindPointVersion(long ruleSetVersionId) {
        long pointVersionId = randomId();
        int versionNo = ThreadLocalRandom.current().nextInt(100_000, 2_000_000_000);
        pointVersionIds.add(pointVersionId);
        mapper.insertPointVersion(pointVersionId, RuleSetBuilder.DECISION_POINT_ID, versionNo,
            RuleSetBuilder.sha256("point" + pointVersionId));
        long bindingId = randomId();
        bindingIds.add(bindingId);
        mapper.insertBinding(bindingId, pointVersionId, ruleSetVersionId);
        return new PointVersion(pointVersionId, versionNo);
    }

    /** 首次使用时取随机基数，此后递增。 */
    private long nextReleaseId() {
        if (nextReleaseId == 0) {
            nextReleaseId = randomId();
        }
        return nextReleaseId++;
    }

    /** 15 位正整数 ID，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
