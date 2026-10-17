package com.paytech.finance.accounting.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.finance.accounting.core.dal.dataobject.ChannelCostComponent;
import com.paytech.finance.accounting.core.dal.dataobject.ChannelCostPlan;
import com.paytech.finance.accounting.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.finance.accounting.core.dal.dataobject.ChannelCostScope;
import com.paytech.finance.accounting.domain.view.ChannelCostVersionView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道成本版本写入：方案、目标版本、适用范围与成本组成；批准生效时附带被替代的原当前版本。 */
@Getter
@AllArgsConstructor
public class ChannelCostVersionContext {
    /** 所属方案；切换当前版本后由 Repository 同步。 */
    private final ChannelCostPlan plan;

    /** 目标版本；状态迁移后由 Repository 同步。 */
    private final ChannelCostPlanVer ver;

    /** 目标版本的全部适用范围。 */
    private final List<ChannelCostScope> scopes;

    /** 全部适用范围的成本组成，以 scopeId 关联。 */
    private final List<ChannelCostComponent> components;

    /** 范围币种到小数位的映射，用于金额规范输出与内容摘要。 */
    private final Map<String, Integer> minorUnits;

    /**
     * 被替代的原当前版本
     * <li>仅批准且已到生效时间、方案已有当前版本时非空。</li>
     */
    private final ChannelCostPlanVer currentVer;

    /** 不涉及当前版本切换的写入。 */
    public ChannelCostVersionContext(ChannelCostPlan plan, ChannelCostPlanVer ver, List<ChannelCostScope> scopes,
        List<ChannelCostComponent> components, Map<String, Integer> minorUnits) {
        this(plan, ver, scopes, components, minorUnits, null);
    }

    /** 批准时刻是否已到目标版本的生效时间。 */
    public boolean effectiveAt(Instant time) {
        return !ver.getEffectiveFrom().isAfter(time);
    }

    /** 按 {@link ChannelCostContentHash} 计算目标版本内容摘要。 */
    public String computeContentHash() {
        return ChannelCostContentHash.compute(plan, ver, scopes, components, minorUnits);
    }

    /** 目标版本详情。 */
    public ChannelCostVersionView toView() {
        return ChannelCostVersionView.of(plan.getPlanCode(), ver, scopes, components, minorUnits);
    }
}
