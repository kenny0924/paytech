package com.paytech.channel.connector.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次渠道成本版本写入：方案、目标版本、适用范围与成本组成；切换当前版本时附带被替代的原当前版本。 */
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

    /**
     * 被替代的原当前版本
     * <li>仅目标版本将成为当前版本、且方案已有当前版本时非空。</li>
     */
    private final ChannelCostPlanVer currentVer;

    /** 不涉及当前版本切换的写入。 */
    public ChannelCostVersionContext(ChannelCostPlan plan, ChannelCostPlanVer ver, List<ChannelCostScope> scopes,
        List<ChannelCostComponent> components) {
        this(plan, ver, scopes, components, null);
    }

    /** 给定时刻是否已到目标版本的生效时间。 */
    public boolean effectiveAt(Instant time) {
        return !ver.getEffectiveFrom().isAfter(time);
    }

    /**
     * 目标版本将替代给定当前版本：其生效时间必须晚于当前版本，避免生效区间倒置
     *
     * @param currentVer 方案当前版本，为空表示尚无当前版本
     */
    public void checkLaterThan(ChannelCostPlanVer currentVer) {
        if (currentVer != null && !ver.getEffectiveFrom().isAfter(currentVer.getEffectiveFrom())) {
            throw new BizCheckFailException("新版本生效时间必须晚于当前版本: 方案 " + plan.getPlanCode() + ", 版本 "
                + ver.getCostVer() + ", 当前版本 " + currentVer.getCostVer());
        }
    }

    /**
     * 目标版本可作废：只能是 APPROVED 且生效时间晚于给定时刻，否则业务拒绝
     * <li>只依据已加载的版本判断；写入时仍以条件 SQL 复核状态与生效时间。</li>
     *
     * @param now 作废时刻
     */
    public void checkCancellableAt(Instant now) {
        if (ver.getStatus() != ChannelCostVersionStatus.APPROVED) {
            throw new BizCheckFailException("只能作废已批准且未到生效时间的渠道成本版本: 方案 " + plan.getPlanCode() + ", 版本 "
                + ver.getCostVer() + ", 当前状态 " + ver.getStatus().getCode());
        }
        if (effectiveAt(now)) {
            throw new BizCheckFailException("已批准版本已到生效时间，不能作废: 方案 " + plan.getPlanCode() + ", 版本 "
                + ver.getCostVer() + ", 当前状态 " + ver.getStatus().getCode() + ", 生效时间 " + ver.getEffectiveFrom());
        }
    }

    /** 以已加载的数据附带被替代的原当前版本，供切换当前版本的写入使用。 */
    public ChannelCostVersionContext replacing(ChannelCostPlanVer replacedVer) {
        return new ChannelCostVersionContext(plan, ver, scopes, components, replacedVer);
    }

    /** 按 {@link ChannelCostContentHash} 计算目标版本内容摘要。 */
    public String computeContentHash() {
        return ChannelCostContentHash.compute(plan, ver, scopes, components);
    }

    /** 目标版本详情。 */
    public ChannelCostVersionView toView() {
        return ChannelCostVersionView.of(plan.getPlanCode(), ver, scopes, components);
    }
}
