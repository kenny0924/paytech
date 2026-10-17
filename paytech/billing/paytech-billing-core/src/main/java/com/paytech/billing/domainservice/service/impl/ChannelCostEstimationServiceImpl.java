package com.paytech.billing.domainservice.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.billing.core.dal.dataobject.ChannelCostComponent;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.core.dal.dataobject.ChannelCostScope;
import com.paytech.billing.core.dal.dataobject.CurrencySpec;
import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostPlanStatus;
import com.paytech.billing.domain.ChannelCostEstimateCommand;
import com.paytech.billing.domain.view.ChannelCostEstimate;
import com.paytech.billing.domainservice.repository.ChannelCostComponentRepository;
import com.paytech.billing.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.billing.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.billing.domainservice.repository.ChannelCostScopeRepository;
import com.paytech.billing.domainservice.repository.CurrencySpecRepository;
import com.paytech.billing.domainservice.service.ChannelCostEstimationService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.AllArgsConstructor;

/**
 * 预计渠道成本估算
 * <li>顺序：校验计费基数与币种 → 按 Channel 取 ACTIVE 方案 → 取覆盖发生时间的唯一 ACTIVE/INACTIVE 版本 → 六维精确命中范围 → 按组成代码升序逐项计算。</li>
 * <li>组成金额：基数 × 费率 + 固定费，按 8 位小数 HALF_UP，再受上下限约束，最后按币种精度与组成舍入方式归整。</li>
 * <li>只读，不开启事务，可在调用方事务内执行；任一步不满足即拒绝，不返回零成本。</li>
 */
@Service
@AllArgsConstructor
public class ChannelCostEstimationServiceImpl implements ChannelCostEstimationService {
    /** 原始金额的计算精度。 */
    static final int RAW_SCALE = 8;

    final CurrencySpecRepository currencySpecRepository;
    final ChannelCostPlanRepository channelCostPlanRepository;
    final ChannelCostPlanVerRepository channelCostPlanVerRepository;
    final ChannelCostScopeRepository channelCostScopeRepository;
    final ChannelCostComponentRepository channelCostComponentRepository;

    @Override
    public ChannelCostEstimate estimate(ChannelCostEstimateCommand command) {
        int minorUnit = validateBasis(command);
        ChannelCostPlan plan = loadActivePlan(command);
        ChannelCostPlanVer ver = loadEffectiveVersion(plan, command);
        ChannelCostScope scope = loadMatchedScope(ver, command);
        List<ChannelCostEstimate.Component> components = calculateComponents(scope, command, minorUnit);
        BigDecimal total = components.stream()
            .map(ChannelCostEstimate.Component::getPostedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(minorUnit, RoundingMode.UNNECESSARY);
        return new ChannelCostEstimate(plan.getId(), ver.getId(), ver.getCostVer(), ver.getContentHash(),
            scope.getId(), scope.getScopeNo(), scope.getChannelAgreementRef(), command.getActionType(),
            command.getCurrency(), components, total);
    }

    /** 校验计费基数、发生时间与币种，返回币种精度。 */
    int validateBasis(ChannelCostEstimateCommand command) {
        BigDecimal basis = command.getBasisAmount();
        if (command.getOccurredAt() == null) {
            throw new BizCheckFailException("发生时间不能为空: " + describe(command));
        }
        if (basis == null || basis.signum() < 0) {
            throw new BizCheckFailException("计费基数不合法: " + basis + ", " + describe(command));
        }
        CurrencySpec spec = currencySpecRepository.findByCurrency(command.getCurrency());
        if (spec == null || spec.getStatus() != CurrencySpecStatus.ACTIVE) {
            throw new BizCheckFailException("币种未配置或未启用: " + describe(command));
        }
        if (basis.stripTrailingZeros().scale() > spec.getMinorUnit()) {
            throw new BizCheckFailException("计费基数不合法: " + basis + ", 小数位超过币种精度 "
                + spec.getMinorUnit() + ", " + describe(command));
        }
        return spec.getMinorUnit();
    }

    /** 按 Channel 读取成本方案，须存在且 ACTIVE。 */
    ChannelCostPlan loadActivePlan(ChannelCostEstimateCommand command) {
        ChannelCostPlan plan = channelCostPlanRepository.findByChannelId(command.getChannelId());
        if (plan == null || plan.getStatus() != ChannelCostPlanStatus.ACTIVE) {
            throw new BizCheckFailException("渠道成本方案不存在或未启用: " + describe(command));
        }
        return plan;
    }

    /** 读取覆盖发生时间的唯一成本版本；多于一个视为数据不一致。 */
    ChannelCostPlanVer loadEffectiveVersion(ChannelCostPlan plan, ChannelCostEstimateCommand command) {
        List<ChannelCostPlanVer> vers = channelCostPlanVerRepository.findEffectiveAt(plan.getId(),
            command.getOccurredAt());
        if (vers.isEmpty()) {
            throw new BizCheckFailException("无覆盖发生时间的渠道成本版本: 方案 " + plan.getPlanCode() + ", "
                + describe(command));
        }
        if (vers.size() > 1) {
            throw new ErrorException("渠道成本版本生效区间重叠, 数据不一致: 方案 " + plan.getPlanCode() + ", 命中 "
                + vers.size() + " 个版本, " + describe(command));
        }
        return vers.get(0);
    }

    /** 在版本内按六个维度精确命中适用范围。 */
    ChannelCostScope loadMatchedScope(ChannelCostPlanVer ver, ChannelCostEstimateCommand command) {
        ChannelCostScope scope = channelCostScopeRepository.findMatch(ver.getId(), command.getMidId(),
            command.getEntityNo(), command.getCountryCode(), command.getCurrency(), command.getPaymentMethodCode(),
            command.getActionType());
        if (scope == null) {
            throw new BizCheckFailException("无匹配的渠道成本适用范围: 版本 " + ver.getCostVer() + ", "
                + describe(command));
        }
        return scope;
    }

    /** 按组成代码升序计算范围内全部组成；范围无组成视为数据不一致。 */
    List<ChannelCostEstimate.Component> calculateComponents(ChannelCostScope scope,
        ChannelCostEstimateCommand command, int minorUnit) {
        List<ChannelCostComponent> components = channelCostComponentRepository.findByScope(scope.getId());
        if (components.isEmpty()) {
            throw new ErrorException("渠道成本适用范围缺少成本组成, 数据不一致: 范围 " + scope.getId() + ", "
                + describe(command));
        }
        return components.stream()
            .map(component -> calculateComponent(component, command.getBasisAmount(), minorUnit))
            .toList();
    }

    /** 计算一个组成的原始金额与归整金额。 */
    ChannelCostEstimate.Component calculateComponent(ChannelCostComponent component, BigDecimal basis,
        int minorUnit) {
        BigDecimal raw = clamp(component, rawAmount(component, basis));
        BigDecimal posted = raw.setScale(minorUnit, toRoundingMode(component.getRoundingMode()));
        return new ChannelCostEstimate.Component(component.getId(), component.getComponentCode(),
            component.getComponentType(), component.getCalculationBase(), component.getRateValue(),
            component.getFixedAmount(), component.getCurrency(), component.getMinAmount(),
            component.getMaxAmount(), component.getRoundingMode(), basis, raw, posted);
    }

    /** 基数 × 费率 + 固定费，按 8 位小数 HALF_UP。 */
    BigDecimal rawAmount(ChannelCostComponent component, BigDecimal basis) {
        BigDecimal raw = BigDecimal.ZERO;
        if (component.getRateValue() != null) {
            raw = raw.add(basis.multiply(component.getRateValue()));
        }
        if (component.getFixedAmount() != null) {
            raw = raw.add(component.getFixedAmount());
        }
        return raw.setScale(RAW_SCALE, RoundingMode.HALF_UP);
    }

    /** 按组成上下限约束原始金额。 */
    BigDecimal clamp(ChannelCostComponent component, BigDecimal raw) {
        if (component.getMinAmount() != null && raw.compareTo(component.getMinAmount()) < 0) {
            return component.getMinAmount().setScale(RAW_SCALE, RoundingMode.HALF_UP);
        }
        if (component.getMaxAmount() != null && raw.compareTo(component.getMaxAmount()) > 0) {
            return component.getMaxAmount().setScale(RAW_SCALE, RoundingMode.HALF_UP);
        }
        return raw;
    }

    /** 组成舍入方式对应的 Java 舍入方式。 */
    RoundingMode toRoundingMode(AmountRoundingMode roundingMode) {
        return switch (roundingMode) {
            case HALF_UP -> RoundingMode.HALF_UP;
        };
    }

    /** 拒绝消息使用的估算上下文。 */
    String describe(ChannelCostEstimateCommand command) {
        return "渠道 " + command.getChannelId() + ", MID " + command.getMidId() + ", 主体 " + command.getEntityNo()
            + ", 国家 " + command.getCountryCode() + ", 币种 " + command.getCurrency() + ", 支付方式 "
            + command.getPaymentMethodCode() + ", 动作 " + command.getActionType() + ", 发生时间 "
            + command.getOccurredAt();
    }
}
