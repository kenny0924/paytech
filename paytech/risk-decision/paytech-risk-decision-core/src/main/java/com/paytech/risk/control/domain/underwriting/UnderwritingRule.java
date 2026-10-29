package com.paytech.risk.control.domain.underwriting;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleThreshold;
import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleVersion;

import lombok.Getter;

/** 一个 ACTIVE 自动承保规则版本及其币种阈值、排除 MCC 集合。 */
@Getter
public final class UnderwritingRule {
    /** 规则版本 ID。 */
    private final long ruleVersionId;

    /** 规则版本号。 */
    private final int versionNo;

    /** 币种到月流水上限（含）。 */
    private final Map<String, BigDecimal> thresholds;

    /** 风控额外排除的 MCC。 */
    private final Set<String> excludedMccCodes;

    /** 由已读取的版本行与阈值行构造。 */
    private UnderwritingRule(long ruleVersionId, int versionNo, Map<String, BigDecimal> thresholds,
        Set<String> excludedMccCodes) {
        this.ruleVersionId = ruleVersionId;
        this.versionNo = versionNo;
        this.thresholds = thresholds;
        this.excludedMccCodes = excludedMccCodes;
    }

    /**
     * 组装规则
     *
     * @param version ACTIVE 规则版本行
     * @param thresholds 该版本的阈值行
     * @return 规则
     */
    public static UnderwritingRule of(UnderwritingRuleVersion version, List<UnderwritingRuleThreshold> thresholds) {
        Map<String, BigDecimal> limits = thresholds.stream().collect(Collectors.toUnmodifiableMap(
            UnderwritingRuleThreshold::getCurrency, UnderwritingRuleThreshold::getMonthlyVolumeLimit));
        String excluded = version.getExcludedMccCodes();
        Set<String> codes = excluded == null || excluded.isBlank() ? Set.of()
            : Arrays.stream(excluded.split(",")).map(String::trim).filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new UnderwritingRule(version.getRuleVersionId(), version.getVersionNo(), limits, codes);
    }
}
