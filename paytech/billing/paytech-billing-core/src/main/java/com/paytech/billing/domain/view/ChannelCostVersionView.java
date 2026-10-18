package com.paytech.billing.domain.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.paytech.billing.core.dal.dataobject.ChannelCostComponent;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.dal.dataobject.ChannelCostScope;
import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostCalculationBase;
import com.paytech.billing.core.enums.ChannelCostComponentType;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道成本版本详情：版本、按 scopeNo 升序的适用范围与范围内按 componentCode 升序的成本组成
 * <li>费率已去掉末尾零，金额已按范围币种精度补齐。</li>
 */
@Getter
@Setter
public class ChannelCostVersionView {
    /** 方案代码。 */
    private String planCode;

    /** 版本号。 */
    private Integer costVer;

    /** 版本状态。 */
    private ChannelCostVersionStatus status;

    /** 开始参与匹配的时间。 */
    private Instant effectiveFrom;

    /**
     * 停止参与匹配的时间
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

    /**
     * 由版本、适用范围与成本组成组装详情
     *
     * @param minorUnits 范围币种到小数位的映射，必须覆盖全部范围币种
     */
    public static ChannelCostVersionView of(String planCode, ChannelCostPlanVer ver, List<ChannelCostScope> scopes,
        List<ChannelCostComponent> components, Map<String, Integer> minorUnits) {
        ChannelCostVersionView view = new ChannelCostVersionView();
        view.setPlanCode(planCode);
        view.setCostVer(ver.getCostVer());
        view.setStatus(ver.getStatus());
        view.setEffectiveFrom(ver.getEffectiveFrom());
        view.setEffectiveTo(ver.getEffectiveTo());
        view.setContentHash(ver.getContentHash());
        view.setMakerId(ver.getMakerId());
        view.setCheckerId(ver.getCheckerId());
        view.setScopes(scopes.stream().sorted(Comparator.comparing(ChannelCostScope::getScopeNo))
            .map(scope -> Scope.of(scope, components, minorUnits.get(scope.getCurrency()))).toList());
        return view;
    }

    /** 适用范围。 */
    @Getter
    @Setter
    public static class Scope {
        /** 版本内范围序号。 */
        private Integer scopeNo;

        /** Payment Channel MID 稳定业务 ID。 */
        private Long midId;

        /** CCT Operating Entity 编号。 */
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
        static Scope of(ChannelCostScope source, List<ChannelCostComponent> components, int minorUnit) {
            Scope scope = new Scope();
            scope.setScopeNo(source.getScopeNo());
            scope.setMidId(source.getMidId());
            scope.setEntityNo(source.getEntityNo());
            scope.setChannelAgreementRef(source.getChannelAgreementRef());
            scope.setCountryCode(source.getCountryCode());
            scope.setCurrency(source.getCurrency());
            scope.setPaymentMethodCode(source.getPaymentMethodCode());
            scope.setActionType(source.getActionType());
            scope.setComponents(components.stream().filter(component -> component.getScopeId().equals(source.getId()))
                .sorted(Comparator.comparing(ChannelCostComponent::getComponentCode))
                .map(component -> Component.of(component, minorUnit)).toList());
            return scope;
        }
    }

    /** 成本组成。 */
    @Getter
    @Setter
    public static class Component {
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
         * 固定费，按币种精度补齐
         * <li>可为空。</li>
         */
        private BigDecimal fixedAmount;

        /**
         * 固定费币种
         * <li>与固定费成对为空。</li>
         */
        private String currency;

        /**
         * 计算结果下限，按币种精度补齐
         * <li>可为空。</li>
         */
        private BigDecimal minAmount;

        /**
         * 计算结果上限，按币种精度补齐
         * <li>可为空。</li>
         */
        private BigDecimal maxAmount;

        /** 归整到币种精度的方式。 */
        private AmountRoundingMode roundingMode;

        /** 由成本组成组装。 */
        static Component of(ChannelCostComponent source, int minorUnit) {
            Component component = new Component();
            component.setComponentCode(source.getComponentCode());
            component.setComponentType(source.getComponentType());
            component.setCalculationBase(source.getCalculationBase());
            component.setRateValue(source.getRateValue() == null ? null : source.getRateValue().stripTrailingZeros());
            component.setFixedAmount(scale(source.getFixedAmount(), minorUnit));
            component.setCurrency(source.getCurrency());
            component.setMinAmount(scale(source.getMinAmount(), minorUnit));
            component.setMaxAmount(scale(source.getMaxAmount(), minorUnit));
            component.setRoundingMode(source.getRoundingMode());
            return component;
        }

        /** 金额按币种精度补齐；为空时保持为空。 */
        static BigDecimal scale(BigDecimal value, int minorUnit) {
            return value == null ? null : value.setScale(minorUnit);
        }
    }
}
