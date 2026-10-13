package com.paytech.risk.test.mocker.decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder.VersionSpec;
import com.paytech.risk.test.mapper.decision.RuleSetTestMapper;

/**
 * 规则集及发布引用的本地数据 Mocker
 * <li>每个版本插入 {@link RuleSetBuilder#MINIMAL_RULE} 对应的规则闭包。ID 与编码随机生成；发布 ID 在单例内递增，使发布 ID 倒序可预期。</li>
 * <li>每个被发布的规则集版本复用一个冻结决策点版本及唯一绑定；Clear 只删除本 Mocker 插入的行。</li>
 * <li>经业务入口创建的规则集使用 {@link #newRuleSetCode()} 登记的随机编码，Clear 按编码删除其根、版本与闭包。</li>
 * <li>动作目录漂移与资源锁属于共享状态，Clear 恢复原动作名称并强制释放锁。</li>
 *
 * @author Kenny
 */
@TestComponent
public class RuleSetMocker implements Clear {

    private final RuleSetTestMapper mapper;

    private final RedissonClient redisson;

    /** 业务入口按编码写入、需按编码清理的规则集。 */
    private final List<String> createdCodes = new ArrayList<>();

    private final List<Long> rejectionReasonIds = new ArrayList<>();

    /** 被临时改名的动作版本及其原名称。 */
    private final Map<Long, String> driftedActionNames = new HashMap<>();

    private final List<RLock> heldLocks = new ArrayList<>();

    private final List<Long> ruleSetIds = new ArrayList<>();

    private final List<Long> versionIds = new ArrayList<>();

    private final List<Long> pointVersionIds = new ArrayList<>();

    private final List<Long> bindingIds = new ArrayList<>();

    private final List<Long> releaseIds = new ArrayList<>();

    /** 规则集版本 ID 到其发布所用决策点版本。 */
    private final Map<Long, PointVersion> pointVersions = new HashMap<>();

    private long nextReleaseId;

    public RuleSetMocker(RuleSetTestMapper mapper, RedissonClient redisson) {
        this.mapper = mapper;
        this.redisson = redisson;
    }

    /** 已插入的规则集版本、写入的内容摘要及唯一规则的 ID（节点、操作数、动作 ID 依次递增）。 */
    public record MockedVersion(long ruleSetVersionId, int versionNo, VersionSpec spec, String contentHash,
        long ruleId) {
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

        /** 根当前指针指向的最新版本。 */
        public MockedVersion current() {
            return versions.getLast();
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
        // 业务入口可能在该编码下追加服务端生成 ID 的新版本，按编码一并清理。
        createdCodes.add(code);
        List<MockedVersion> versions = new ArrayList<>();
        for (VersionSpec spec : builder.versionSpecs()) {
            int versionNo = versions.size() + 1;
            long versionId = randomId();
            versionIds.add(versionId);
            String contentHash = builder.contentHashOf(code, spec.ruleSetName(), versionNo);
            mapper.insertVersion(versionId, RuleSetBuilder.DECISION_POINT_ID, code, spec.ruleSetName(), versionNo,
                spec.status(), contentHash, spec.createdBy(), spec.changeReason(), spec.createTime(),
                builder.contentFormat());
            long ruleId = randomId();
            if (builder.isVariableReference()) {
                mapper.insertVariableRule(ruleId, versionId, RuleSetBuilder.ALLOW_ACTION_VERSION_ID);
            } else {
                mapper.insertMinimalRule(ruleId, versionId, RuleSetBuilder.ALLOW_ACTION_VERSION_ID);
            }
            versions.add(new MockedVersion(versionId, versionNo, spec, contentHash, ruleId));
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

    /** 登记一个将经业务入口创建的随机规则集编码，Clear 时按编码清理。 */
    public String newRuleSetCode() {
        String code = "MOCK_RULE_SET_" + randomId();
        createdCodes.add(code);
        return code;
    }

    /** 让根的当前指针指向非最新版本，构造管理根损坏。 */
    public void corruptCurrentVersion(MockedRuleSet ruleSet, int versionNo) {
        mapper.updateCurrentVersion(ruleSet.ruleSetId(), ruleSet.version(versionNo).ruleSetVersionId());
    }

    /** 改写种子 ALLOW_CHECKOUT 的持久化名称，使动作目录与工程注册表漂移；Clear 恢复原名。 */
    public void driftActionCatalog() {
        long actionVersionId = RuleSetBuilder.ALLOW_ACTION_VERSION_ID;
        driftedActionNames.putIfAbsent(actionVersionId, mapper.selectActionName(actionVersionId));
        mapper.updateActionName(actionVersionId, "Drifted Allow Checkout");
    }

    /** 插入指定状态的拒绝原因字典项，返回其随机编码。 */
    public String mockRejectionReason(DictionaryStatus status) {
        long reasonId = randomId();
        String code = "MOCK_REASON_" + reasonId;
        rejectionReasonIds.add(reasonId);
        mapper.insertRejectionReason(reasonId, code, status.getCode());
        return code;
    }

    /** 由测试线程持有规则集资源锁，模拟另一命令正在处理同一规则集；Clear 强制释放。 */
    public void holdResourceLock(String ruleSetCode) {
        RLock lock = redisson.getLock("decision:rule-set:" + RuleSetBuilder.POINT_CODE + ":" + ruleSetCode);
        lock.lock(30, TimeUnit.SECONDS);
        heldLocks.add(lock);
    }

    /** 删除本 Mocker 插入的全部数据；删除失败时保留记录，下一次清理继续重试。 */
    @Override
    public void clear() {
        heldLocks.forEach(RLock::forceUnlock);
        heldLocks.clear();
        driftedActionNames.forEach(mapper::updateActionName);
        driftedActionNames.clear();
        createdCodes.forEach(code -> mapper.deleteByCode(RuleSetBuilder.DECISION_POINT_ID, code));
        createdCodes.clear();
        mapper.deleteRejectionReasons(rejectionReasonIds);
        rejectionReasonIds.clear();
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
