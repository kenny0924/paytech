package com.paytech.commercial.service.processor.request;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** DRAFT 价格集合整体替换的内部请求；列表顺序即 component_no / band_no。 */
@Getter
@AllArgsConstructor
public class ReplaceCommercialTermPricesRequest {
    /** 版本 ID */
    @Positive
    private final long versionId;

    /** 方案根期望版本 */
    @Positive
    private final long expectedChangeVersion;

    /** 操作人账号 ID */
    @Positive
    private final long operatorAccountId;

    /** 完整价格集合 */
    @NotNull
    @Size(max = 1000)
    @Valid
    private final List<PriceItem> prices;

    /** 一条价格 */
    @Getter
    @AllArgsConstructor
    public static class PriceItem {
        /** 准确 OE */
        @NotNull
        private final String entityNo;

        /** 币种 */
        @NotNull
        private final String currency;

        /** 画像代码 */
        @NotNull
        private final String pricingProfileCode;

        /** 优先级 */
        @NotNull
        private final Integer priorityNo;

        /** T+N 工作日结算天数 */
        private final Integer settlementBusinessDays;

        /** 工作日日历代码 */
        private final String businessCalendarCode;

        /** 结算时区 */
        private final String settlementTimezone;

        /** 截止秒数 */
        private final Integer cutoffSecondOfDay;

        /** 组件 */
        @NotNull
        @Size(min = 1)
        @Valid
        private final List<ComponentItem> components;

        /** 来源模板价格 ID，可空 */
        private final Long sourceTemplatePriceId;
    }

    /** 一个组件 */
    @Getter
    @AllArgsConstructor
    public static class ComponentItem {
        /** 组件代码 */
        @NotNull
        private final String componentCode;

        /** 组件类型 */
        @NotNull
        private final CommercialTermComponentComponentType componentType;

        /** 触发计费事件 */
        @NotNull
        private final String triggerCode;

        /** 计算基数 */
        @NotNull
        private final CalculationBase calculationBase;

        /** 退款处理 */
        @NotNull
        private final RefundBehavior refundBehavior;

        /** 费率 */
        private final BigDecimal rate;

        /** 固定金额 */
        private final BigDecimal fixedAmount;

        /** 币种 */
        private final String currency;

        /** 最低金额 */
        private final BigDecimal minAmount;

        /** 最高金额 */
        private final BigDecimal maxAmount;

        /** 阶梯基数 */
        private final BandBasis bandBasis;

        /** 阶梯模式 */
        private final CommercialTermComponentBandMode bandMode;

        /** 档位 */
        @NotNull
        @Valid
        private final List<BandItem> bands;
    }

    /** 一档 */
    @Getter
    @AllArgsConstructor
    public static class BandItem {
        /** 下界 */
        @NotNull
        private final BigDecimal lowerBound;

        /** 上界 */
        private final BigDecimal upperBound;

        /** 费率 */
        private final BigDecimal rate;

        /** 固定金额 */
        private final BigDecimal fixedAmount;
    }
}
