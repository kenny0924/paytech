package com.paytech.billing.domainservice.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.domain.ChannelCostComponentAmount;
import com.paytech.billing.domain.ChannelCostComponentRule;
import com.paytech.billing.domainservice.service.ChannelCostCalculator;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 渠道成本组件金额计算（由原 ChannelCostEstimationServiceImpl 的逐组件计算部分搬入）
 * <li>原始金额：基数 × 费率 + 固定费，按 8 位小数 HALF_UP，再受上下限约束；记账金额按币种精度与组件舍入方式归整。</li>
 * <li>无状态纯计算，不读库、不调用外部依赖。</li>
 */
@Service
public class ChannelCostCalculatorImpl implements ChannelCostCalculator {
    /** 原始金额的计算精度。 */
    static final int RAW_SCALE = 8;

    /** 校验输入后逐组件计算，保持输入顺序。 */
    @Override
    public List<ChannelCostComponentAmount> calculate(List<ChannelCostComponentRule> components, BigDecimal basisAmount,
        int minorUnit) {
        if (components == null || components.isEmpty()) {
            throw new BizCheckFailException("渠道成本组件为空，不能计算成本");
        }
        if (minorUnit < 0 || minorUnit > RAW_SCALE) {
            throw new BizCheckFailException("币种精度不合法: " + minorUnit);
        }
        if (basisAmount == null || basisAmount.signum() < 0
            || basisAmount.stripTrailingZeros().scale() > minorUnit) {
            throw new BizCheckFailException("计费基数不合法: " + basisAmount + ", 币种精度 " + minorUnit);
        }
        return components.stream().map(component -> calculate(component, basisAmount, minorUnit)).toList();
    }

    /** 计算一个组件的原始金额与归整金额。 */
    ChannelCostComponentAmount calculate(ChannelCostComponentRule component, BigDecimal basis, int minorUnit) {
        BigDecimal raw = clamp(component, rawAmount(component, basis));
        BigDecimal posted = raw.setScale(minorUnit, toRoundingMode(component.getRoundingMode()));
        return new ChannelCostComponentAmount(component, basis, component.getRateValue(), raw, posted);
    }

    /** 基数 × 费率 + 固定费，按 8 位小数 HALF_UP。 */
    BigDecimal rawAmount(ChannelCostComponentRule component, BigDecimal basis) {
        BigDecimal raw = BigDecimal.ZERO;
        if (component.getRateValue() != null) {
            raw = raw.add(basis.multiply(component.getRateValue()));
        }
        if (component.getFixedAmount() != null) {
            raw = raw.add(component.getFixedAmount());
        }
        return raw.setScale(RAW_SCALE, RoundingMode.HALF_UP);
    }

    /** 按组件上下限约束原始金额。 */
    BigDecimal clamp(ChannelCostComponentRule component, BigDecimal raw) {
        if (component.getMinAmount() != null && raw.compareTo(component.getMinAmount()) < 0) {
            return component.getMinAmount().setScale(RAW_SCALE, RoundingMode.HALF_UP);
        }
        if (component.getMaxAmount() != null && raw.compareTo(component.getMaxAmount()) > 0) {
            return component.getMaxAmount().setScale(RAW_SCALE, RoundingMode.HALF_UP);
        }
        return raw;
    }

    /** 组件舍入方式对应的 Java 舍入方式。 */
    RoundingMode toRoundingMode(AmountRoundingMode roundingMode) {
        if (roundingMode == null) {
            throw new BizCheckFailException("渠道成本组件缺少舍入方式");
        }
        return switch (roundingMode) {
            case HALF_UP -> RoundingMode.HALF_UP;
        };
    }
}
