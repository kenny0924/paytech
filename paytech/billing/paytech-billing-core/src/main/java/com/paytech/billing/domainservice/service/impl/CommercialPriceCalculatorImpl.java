package com.paytech.billing.domainservice.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.paytech.billing.api.enums.CalculationMethod;
import com.paytech.billing.domain.CommercialPrice;
import com.paytech.billing.domain.CommercialPriceBand;
import com.paytech.billing.domain.CommercialPriceComponent;
import com.paytech.billing.domain.FeeCalculationBasis;
import com.paytech.billing.domain.PriceComponentAmount;
import com.paytech.billing.domainservice.service.CommercialPriceCalculator;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * commercial 价格组件金额计算（技术方案 4.6.1）
 * <li>基数：GROSS_AMOUNT 为毛额；ACTUAL_CLIENT_FEE 为 Σ CLIENT_FEE、NET_AMOUNT 为毛额 − Σ CLIENT_FEE，只允许 Partner 条款；
 * FIXED_PER_EVENT 不参与计算，记录毛额为基数。</li>
 * <li>PERCENTAGE：基数 × 费率；FIXED：固定金额；TIERED（阶梯基数只接受 TRANSACTION_AMOUNT）：WHOLE_BASE 按基数所在档整体计算，
 * PROGRESSIVE 按 [下界, 上界) 逐档累进，每个计入的档加其固定金额。</li>
 * <li>原始金额先受下限再受上限约束，按 8 位小数 HALF_UP；记账金额按币种精度 HALF_UP（与 ChannelCostCalculator 一致）。</li>
 * <li>commercial 的组件类型与阶梯模式代码只在其 core 内定义，这里按稳定代码文本识别。</li>
 */
@Service
public class CommercialPriceCalculatorImpl implements CommercialPriceCalculator {
    /** 原始金额的计算精度。 */
    static final int RAW_SCALE = 8;

    /** 百分比组件。 */
    static final String PERCENTAGE = "PERCENTAGE";

    /** 固定金额组件。 */
    static final String FIXED = "FIXED";

    /** 阶梯组件。 */
    static final String TIERED = "TIERED";

    /** 阶梯：按基数所在档整体计算。 */
    static final String WHOLE_BASE = "WHOLE_BASE";

    /** 阶梯：逐档累进。 */
    static final String PROGRESSIVE = "PROGRESSIVE";

    /** 只计算 SALE 组件，按组件序号升序。 */
    @Override
    public List<PriceComponentAmount> calculate(CommercialPrice price, FeeCalculationBasis basis) {
        return price.getComponents().stream()
            .filter(component -> CommercialCodes.FeeEvent.SALE.equals(component.getTriggerCode()))
            .sorted(Comparator.comparingInt(CommercialPriceComponent::getComponentNo))
            .map(component -> calculate(price, component, basis)).toList();
    }

    /** 按组件类型计算一个组件。 */
    PriceComponentAmount calculate(CommercialPrice price, CommercialPriceComponent component, FeeCalculationBasis basis) {
        BigDecimal base = resolveBasis(price, component, basis);
        return switch (component.getComponentType()) {
            case PERCENTAGE -> percentage(price, component, base, basis);
            case FIXED -> fixed(price, component, base, basis);
            case TIERED -> tiered(price, component, base, basis);
            default -> throw rejected(price, component, "组件类型本期不支持 " + component.getComponentType());
        };
    }

    /** 计算基数；自引用、BUY_RATE_DIFFERENCE 与未知基数拒绝。 */
    BigDecimal resolveBasis(CommercialPrice price, CommercialPriceComponent component, FeeCalculationBasis basis) {
        String base = component.getCalculationBase();
        if (CalculationBase.GROSS_AMOUNT.getCode().equals(base) || CalculationBase.FIXED_PER_EVENT.getCode().equals(base)) {
            return basis.getGrossAmount();
        }
        boolean clientFeeBase = CalculationBase.ACTUAL_CLIENT_FEE.getCode().equals(base)
            || CalculationBase.NET_AMOUNT.getCode().equals(base);
        if (!clientFeeBase) {
            throw rejected(price, component, "计算基数本期不支持 " + base);
        }
        if (basis.getClientFeeAmount() == null) {
            throw rejected(price, component, "Client 定价不得引用 Client 手续费基数 " + base);
        }
        return CalculationBase.ACTUAL_CLIENT_FEE.getCode().equals(base) ? basis.getClientFeeAmount()
            : basis.getGrossAmount().subtract(basis.getClientFeeAmount());
    }

    /** 基数 × 费率；按次固定基数不能用于百分比。 */
    PriceComponentAmount percentage(CommercialPrice price, CommercialPriceComponent component, BigDecimal base,
        FeeCalculationBasis basis) {
        if (component.getRate() == null || fixedPerEvent(component)) {
            throw rejected(price, component, "百分比组件缺少费率或使用了按次基数");
        }
        return finish(price, component, CalculationMethod.PERCENTAGE, base, component.getRate(), null,
            base.multiply(component.getRate()), basis);
    }

    /** 固定金额，币种必须等于事实币种（S9）。 */
    PriceComponentAmount fixed(CommercialPrice price, CommercialPriceComponent component, BigDecimal base,
        FeeCalculationBasis basis) {
        if (component.getFixedAmount() == null) {
            throw rejected(price, component, "固定金额组件缺少固定金额");
        }
        requireFactCurrency(price, component, basis);
        return finish(price, component, CalculationMethod.FIXED, base, null, null, component.getFixedAmount(), basis);
    }

    /** 阶梯：只接受按交易金额分档，基数必须落在某一档。 */
    PriceComponentAmount tiered(CommercialPrice price, CommercialPriceComponent component, BigDecimal base,
        FeeCalculationBasis basis) {
        if (!BandBasis.TRANSACTION_AMOUNT.getCode().equals(component.getBandBasis()) || fixedPerEvent(component)) {
            throw rejected(price, component, "阶梯基数本期不支持 " + component.getBandBasis());
        }
        CommercialPriceBand band = component.getBands().stream().filter(candidate -> candidate.contains(base)).findFirst()
            .orElseThrow(() -> rejected(price, component, "基数落不进任何档位"));
        if (component.getBands().stream().anyMatch(candidate -> candidate.getFixedAmount() != null)) {
            requireFactCurrency(price, component, basis);
        }
        if (WHOLE_BASE.equals(component.getBandMode())) {
            return finish(price, component, CalculationMethod.TIERED_WHOLE_BASE, base, band.getRate(), band,
                bandAmount(band, base), basis);
        }
        if (PROGRESSIVE.equals(component.getBandMode())) {
            return finish(price, component, CalculationMethod.TIERED_PROGRESSIVE, base, null, band,
                progressiveAmount(component, base), basis);
        }
        throw rejected(price, component, "阶梯模式本期不支持 " + component.getBandMode());
    }

    /** 一档的金额：部分基数 × 档位费率 + 档位固定金额（为空的项不计）。 */
    static BigDecimal bandAmount(CommercialPriceBand band, BigDecimal portion) {
        BigDecimal amount = band.getRate() == null ? BigDecimal.ZERO : portion.multiply(band.getRate());
        return band.getFixedAmount() == null ? amount : amount.add(band.getFixedAmount());
    }

    /** 逐档累进：下界不超过基数的每一档（含基数所在档）按 [下界, min(上界, 基数)) 部分计入。 */
    static BigDecimal progressiveAmount(CommercialPriceComponent component, BigDecimal base) {
        BigDecimal amount = BigDecimal.ZERO;
        List<CommercialPriceBand> bands = component.getBands().stream()
            .sorted(Comparator.comparingInt(CommercialPriceBand::getBandNo)).toList();
        for (CommercialPriceBand band : bands) {
            if (band.getLowerBound().compareTo(base) > 0) {
                continue;
            }
            BigDecimal upper = band.getUpperBound() == null ? base : band.getUpperBound().min(base);
            amount = amount.add(bandAmount(band, upper.subtract(band.getLowerBound()).max(BigDecimal.ZERO)));
        }
        return amount;
    }

    /** 上下限约束、8 位原始金额与币种精度记账金额。 */
    PriceComponentAmount finish(CommercialPrice price, CommercialPriceComponent component, CalculationMethod method,
        BigDecimal base, BigDecimal rate, CommercialPriceBand band, BigDecimal amount, FeeCalculationBasis basis) {
        if (component.getMinAmount() != null || component.getMaxAmount() != null) {
            requireFactCurrency(price, component, basis);
        }
        BigDecimal raw = amount;
        if (component.getMinAmount() != null) {
            raw = raw.max(component.getMinAmount());
        }
        if (component.getMaxAmount() != null) {
            raw = raw.min(component.getMaxAmount());
        }
        raw = raw.setScale(RAW_SCALE, RoundingMode.HALF_UP);
        BigDecimal posted = raw.setScale(basis.getMinorUnit(), RoundingMode.HALF_UP);
        return new PriceComponentAmount(component, method, base, rate, band, raw, posted);
    }

    /** 组件给出的绝对金额（固定金额、上下限、档位固定金额）必须与事实同币种，不做换汇（S9）。 */
    void requireFactCurrency(CommercialPrice price, CommercialPriceComponent component, FeeCalculationBasis basis) {
        if (!Objects.equals(component.getCurrency(), basis.getCurrency())) {
            throw rejected(price, component, "组件金额币种 " + component.getCurrency() + " 与事实币种 "
                + basis.getCurrency() + " 不同");
        }
    }

    /** 组件使用按次固定基数。 */
    static boolean fixedPerEvent(CommercialPriceComponent component) {
        return CalculationBase.FIXED_PER_EVENT.getCode().equals(component.getCalculationBase());
    }

    /** 带条款、价格与组件上下文的业务拒绝。 */
    static BizCheckFailException rejected(CommercialPrice price, CommercialPriceComponent component, String reason) {
        return new BizCheckFailException("价格组件本期不能计算: " + reason + ", 条款 " + price.getTermType() + ", 价格 "
            + price.getPriceId() + ", 组件 " + component.getComponentCode());
    }
}
