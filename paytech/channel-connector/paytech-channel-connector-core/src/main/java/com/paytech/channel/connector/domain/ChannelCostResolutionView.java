package com.paytech.channel.connector.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本命中结果：方案、覆盖命中时间的版本、唯一命中范围及其按 componentCode 升序的成本组成。 */
@Getter
@Setter
public class ChannelCostResolutionView {
    /** 方案代码。 */
    private String planCode;

    /** 命中版本主键。 */
    private Long costVerId;

    /** 方案内版本号。 */
    private Integer costVer;

    /** 版本内容摘要。 */
    private String contentHash;

    /** 版本开始参与命中的时间。 */
    private Instant effectiveFrom;

    /**
     * 版本停止参与命中的时间
     * <li>长期有效时为空。</li>
     */
    private Instant effectiveTo;

    /** 命中范围主键。 */
    private Long scopeId;

    /** 版本内范围序号。 */
    private Integer scopeNo;

    /** 该范围实际采用的 Channel 合同或费率文件稳定引用。 */
    private String channelAgreementRef;

    /** 范围币种，也是成本金额的币种。 */
    private String currency;

    /** 按 componentCode 升序的成本组成。 */
    private List<ChannelCostVersionView.Component> components;

    /** 由命中的方案、版本、范围与该范围成本组成组装。 */
    public static ChannelCostResolutionView of(ChannelCostPlan plan, ChannelCostPlanVer ver, ChannelCostScope scope,
        List<ChannelCostComponent> components) {
        ChannelCostResolutionView view = new ChannelCostResolutionView();
        view.setPlanCode(plan.getPlanCode());
        view.setCostVerId(ver.getId());
        view.setCostVer(ver.getCostVer());
        view.setContentHash(ver.getContentHash());
        view.setEffectiveFrom(ver.getEffectiveFrom());
        view.setEffectiveTo(ver.getEffectiveTo());
        view.setScopeId(scope.getId());
        view.setScopeNo(scope.getScopeNo());
        view.setChannelAgreementRef(scope.getChannelAgreementRef());
        view.setCurrency(scope.getCurrency());
        view.setComponents(ChannelCostVersionView.Component.ofScope(scope, components));
        return view;
    }
}
