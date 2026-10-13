package com.paytech.risk.test.builder.decision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.*;

/**
 * 规则集整体根及其版本序列的测试 Builder
 * <li>版本按序号 1..N 排列，最后一个版本为根的当前配置指针。</li>
 * <li>每个版本带同一条最小规则 {@link #MINIMAL_RULE}：country_code 非空即 ALLOW_CHECKOUT，满足闭包完整性。</li>
 * <li>内容摘要由生产 Canonicalizer 按同一规则投影计算，仅用于准备合法夹具；断言不使用该算法。</li>
 *
 * @author Kenny
 */
public final class RuleSetBuilder {
    /** 种子数据中的决策点。 */
    public static final long DECISION_POINT_ID = 210001L;

    public static final String POINT_CODE = "CHECKOUT_PREPARE";

    /** 版本时间基准，均为 UTC 微秒精度。 */
    static final Instant BASE_TIME = Instant.parse("2026-01-02T03:04:05.000001Z");

    /** 一个版本的保存事实。 */
    public record VersionSpec(RuleSetVersionStatus status, String ruleSetName, String changeReason, long createdBy,
        Instant createTime) {
    }

    private final List<VersionSpec> versions = new ArrayList<>();

    private boolean deleted;

    private RuleSetBuilder() {}

    /** 按给定状态依次生成版本 1..N，最后一个为当前版本，须为 DRAFT 或 FROZEN。 */
    public static RuleSetBuilder versions(RuleSetVersionStatus... statuses) {
        RuleSetBuilder builder = new RuleSetBuilder();
        for (RuleSetVersionStatus status : statuses) {
            int versionNo = builder.versions.size() + 1;
            builder.versions.add(new VersionSpec(status, "Checkout rules v" + versionNo, "change " + versionNo,
                70000L + versionNo, BASE_TIME.plusSeconds(3600L * versionNo).plusNanos(1000L * versionNo)));
        }
        return builder;
    }

    /** 规则集已被整体删除，携带完整删除证据。 */
    public RuleSetBuilder deleted() {
        this.deleted = true;
        return this;
    }

    public List<VersionSpec> versionSpecs() {
        return List.copyOf(versions);
    }

    public boolean isDeleted() {
        return deleted;
    }

    /** 最小规则：与 Mocker 插入的规则、节点、操作数及终止动作行一一对应。 */
    public static final RuleProjection MINIMAL_RULE = new RuleProjection("MOCK_RULE", "Mock rule", 10, true, null,
        new PredicateProjection(ComparisonOperator.IS_NOT_NULL,
            List.of(new OperandProjection(OperandRole.LEFT, 1, OperandSource.CONTEXT, "country_code",
                DecisionValueType.STRING, NullValue.INSTANCE))),
        new ActionProjection("ALLOW_CHECKOUT", 1, "DECISION_ALLOW_V1", "1.0.0",
            "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1", ActionCategory.DECISION, true,
            DecisionValue.ALLOW, MergePolicy.MOST_RESTRICTIVE, true));

    /** 种子数据中 ALLOW_CHECKOUT 的动作版本 ID。 */
    public static final long ALLOW_ACTION_VERSION_ID = 240001L;

    /** 含最小规则的版本内容摘要。 */
    public static String contentHash(String ruleSetCode, String ruleSetName, int versionNo) {
        return new RiskDecisionCanonicalizerImpl()
            .ruleSetVersion(
                new RuleSetVersionProjection(POINT_CODE, ruleSetCode, ruleSetName, versionNo, List.of(MINIMAL_RULE)))
            .sha256LowerHex();
    }

    /** 计算小写十六进制 SHA-256。 */
    public static String sha256(String value) {
        try {
            return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
