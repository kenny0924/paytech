package com.paytech.channel.connector.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;
import com.paytech.channel.connector.core.enums.AmountRoundingMode;
import com.paytech.channel.connector.core.enums.ChannelCostCalculationBase;
import com.paytech.channel.connector.core.enums.ChannelCostComponentType;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道成本版本详情：版本、按 scopeNo 升序的适用范围与范围内按 componentCode 升序的成本组成
 * <li>费率与金额已去掉末尾零；本库无币种精度表，按币种精度补齐由 billing 计算时完成。</li>
 */
@Getter
@Setter
public class ChannelCostVersionView {
    /** 方案代码。 */
    private String planCode;

    /** 版本主键；变更记录以 CHANNEL_COST_VERSION + 该主键定位。 */
    private Long costVerId;

    /** 版本号。 */
    private Integer costVer;

    /** 版本状态。 */
    private ChannelCostVersionStatus status;

    /** 开始参与命中的时间。 */
    private Instant effectiveFrom;

    /**
     * 停止参与命中的时间
     * <li>长期有效时为空。</li>
     */
    private Instant effectiveTo;

    /** 版本内容摘要。 */
    private String contentHash;

    /** 编制人。 */
    private Long makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private Long checkerId;

    /** 按 scopeNo 升序的适用范围。 */
    private List<Scope> scopes;

    /** 由版本、适用范围与成本组成组装详情。 */
    public static ChannelCostVersionView of(String planCode, ChannelCostPlanVer ver, List<ChannelCostScope> scopes,
        List<ChannelCostComponent> components) {
        ChannelCostVersionView view = new ChannelCostVersionView();
        view.setPlanCode(planCode);
        view.setCostVerId(ver.getId());
        view.setCostVer(ver.getCostVer());
        view.setStatus(ver.getStatus());
        view.setEffectiveFrom(ver.getEffectiveFrom());
        view.setEffectiveTo(ver.getEffectiveTo());
        view.setContentHash(ver.getContentHash());
        view.setMakerId(ver.getMakerId());
        view.setCheckerId(ver.getCheckerId());
        view.setScopes(scopes.stream().sorted(Comparator.comparing(ChannelCostScope::getScopeNo))
            .map(scope -> Scope.of(scope, components)).toList());
        return view;
    }

    /** 适用范围。 */
    @Getter
    @Setter
    public static class Scope {
        /** 版本内范围序号。 */
        private Integer scopeNo;

        /** 本库 MID 主键。 */
        private Long midId;

        /** Operating Entity 编号。 */
        private String entityNo;

        /** Channel 合同或费率文件稳定引用。 */
        private String channelAgreementRef;

        /** ISO 3166-1 alpha-2 国家代码。 */
        private String countryCode;

        /** ISO 4217 币种代码。 */
        private String currency;

        /** Payment 支付方式目录代码。 */
        private String paymentMethodCode;

        /** Payment 动作类别。 */
        private String actionType;

        /** 按组成代码升序的成本组成。 */
        private List<Component> components;

        /** 由适用范围及属于它的成本组成组装。 */
        static Scope of(ChannelCostScope source, List<ChannelCostComponent> components) {
            Scope scope = new Scope();
            scope.setScopeNo(source.getScopeNo());
            scope.setMidId(source.getMidId());
            scope.setEntityNo(source.getEntityNo());
            scope.setChannelAgreementRef(source.getChannelAgreementRef());
            scope.setCountryCode(source.getCountryCode());
            scope.setCurrency(source.getCurrency());
            scope.setPaymentMethodCode(source.getPaymentMethodCode());
            scope.setActionType(source.getActionType());
            scope.setComponents(Component.ofScope(source, components));
            return scope;
        }
    }

    /** 成本组成。 */
    @Getter
    @Setter
    public static class Component {
        /** 成本组成主键。 */
        private Long componentId;

        /** 范围内组成代码。 */
        private String componentCode;

        /** 收费语义。 */
        private ChannelCostComponentType componentType;

        /** 金额计算基数。 */
        private ChannelCostCalculationBase calculationBase;

        /**
         * 十进制费率，已去掉末尾零
         * <li>可为空。</li>
         */
        private BigDecimal rateValue;

        /**
         * 固定费，已去掉末尾零
         * <li>可为空。</li>
         */
        private BigDecimal fixedAmount;

        /**
         * 固定费币种
         * <li>与固定费成对为空。</li>
         */
        private String currency;

        /**
         * 计算结果下限，已去掉末尾零
         * <li>可为空。</li>
         */
        private BigDecimal minAmount;

        /**
         * 计算结果上限，已去掉末尾零
         * <li>可为空。</li>
         */
        private BigDecimal maxAmount;

        /** 归整到币种精度的方式。 */
        private AmountRoundingMode roundingMode;

        /** 属于给定范围的成本组成，按组成代码升序组装。 */
        public static List<Component> ofScope(ChannelCostScope scope, List<ChannelCostComponent> components) {
            return components.stream().filter(component -> component.getScopeId().equals(scope.getId()))
                .sorted(Comparator.comparing(ChannelCostComponent::getComponentCode)).map(Component::of).toList();
        }

        /** 由成本组成组装。 */
        static Component of(ChannelCostComponent source) {
            Component component = new Component();
            component.setComponentId(source.getId());
            component.setComponentCode(source.getComponentCode());
            component.setComponentType(source.getComponentType());
            component.setCalculationBase(source.getCalculationBase());
            component.setRateValue(strip(source.getRateValue()));
            component.setFixedAmount(strip(source.getFixedAmount()));
            component.setCurrency(source.getCurrency());
            component.setMinAmount(strip(source.getMinAmount()));
            component.setMaxAmount(strip(source.getMaxAmount()));
            component.setRoundingMode(source.getRoundingMode());
            return component;
        }

        /** 去掉末尾零；为空时保持为空。 */
        static BigDecimal strip(BigDecimal value) {
            return value == null ? null : value.stripTrailingZeros();
        }
    }
}
