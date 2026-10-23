package com.paytech.billing.test.builder;

import static com.paytech.billing.test.builder.PaymentFactConstants.USD;

import java.util.ArrayList;
import java.util.List;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.api.price.response.CommercialPriceBandResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceComponentResponseBody;

/**
 * commercial 价格组件：percentage / fixed / tiered 给出 SALE 触发、GROSS_AMOUNT 基数、USD 的合法组件，覆盖方法只改场景相关字段
 * <li>组件类型与阶梯模式代码由 commercial core 定义，未发布到 api，这里以代码文本给出。</li>
 */
public final class CommercialPriceComponentBuilder {
    /** 阶梯：整体计算。 */
    public static final String WHOLE_BASE = "WHOLE_BASE";

    /** 阶梯：逐档累进。 */
    public static final String PROGRESSIVE = "PROGRESSIVE";

    private final CommercialPriceComponentResponseBody body = new CommercialPriceComponentResponseBody();

    /** 只能从具名模板开始。 */
    private CommercialPriceComponentBuilder(long componentId, String componentType) {
        body.setComponentId(Long.toString(componentId));
        body.setComponentNo(1);
        body.setComponentCode(componentType + "_" + componentId);
        body.setComponentType(componentType);
        body.setTriggerCode(CommercialCodes.FeeEvent.SALE);
        body.setCalculationBase(CalculationBase.GROSS_AMOUNT.getCode());
        body.setRefundBehavior(RefundBehavior.KEEP.getCode());
        body.setCurrency(USD);
        body.setBands(new ArrayList<>());
    }

    /** 按毛额百分比。 */
    public static CommercialPriceComponentBuilder percentage(long componentId, String rate) {
        CommercialPriceComponentBuilder builder = new CommercialPriceComponentBuilder(componentId, "PERCENTAGE");
        builder.body.setRate(rate);
        return builder;
    }

    /** 每笔固定金额（基数 FIXED_PER_EVENT）。 */
    public static CommercialPriceComponentBuilder fixed(long componentId, String fixedAmount) {
        CommercialPriceComponentBuilder builder = new CommercialPriceComponentBuilder(componentId, "FIXED");
        builder.body.setFixedAmount(fixedAmount);
        builder.body.setCalculationBase(CalculationBase.FIXED_PER_EVENT.getCode());
        return builder;
    }

    /** 按交易金额分档。 */
    public static CommercialPriceComponentBuilder tiered(long componentId, String bandMode,
        CommercialPriceBandResponseBody... bands) {
        CommercialPriceComponentBuilder builder = new CommercialPriceComponentBuilder(componentId, "TIERED");
        builder.body.setBandBasis(BandBasis.TRANSACTION_AMOUNT.getCode());
        builder.body.setBandMode(bandMode);
        builder.body.setBands(new ArrayList<>(List.of(bands)));
        return builder;
    }

    /** 一个档位 [lower, upper)，upper 为空表示无上限。 */
    public static CommercialPriceBandResponseBody band(long bandId, int bandNo, String lower, String upper, String rate) {
        CommercialPriceBandResponseBody band = new CommercialPriceBandResponseBody();
        band.setBandId(Long.toString(bandId));
        band.setBandNo(bandNo);
        band.setLowerBound(lower);
        band.setUpperBound(upper);
        band.setRate(rate);
        band.setCurrency(USD);
        return band;
    }

    /** 计算基数。 */
    public CommercialPriceComponentBuilder base(CalculationBase base) {
        body.setCalculationBase(base.getCode());
        return this;
    }

    /** 触发计费事件。 */
    public CommercialPriceComponentBuilder trigger(String triggerCode) {
        body.setTriggerCode(triggerCode);
        return this;
    }

    /** 下限。 */
    public CommercialPriceComponentBuilder min(String minAmount) {
        body.setMinAmount(minAmount);
        return this;
    }

    /** 上限。 */
    public CommercialPriceComponentBuilder max(String maxAmount) {
        body.setMaxAmount(maxAmount);
        return this;
    }

    /** 金额币种。 */
    public CommercialPriceComponentBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 阶梯基数。 */
    public CommercialPriceComponentBuilder bandBasis(BandBasis bandBasis) {
        body.setBandBasis(bandBasis.getCode());
        return this;
    }

    /** 构建。 */
    public CommercialPriceComponentResponseBody build() {
        return body;
    }
}
