package com.paytech.commercial.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_commercial_term_component：价格下的计算组件。
 * <li>按 component_no 稳定计算并相加；TIERED 头部 rate/fixed_amount 为空，band_basis、band_mode 非空且至少一档。</li>
 */
@Getter
@Setter
public class CommercialTermComponent {
    /** 组件标识 */
    private Long termComponentId;

    /** 所属价格 */
    private Long termPriceId;

    /** 价格内从 1 连续的计算顺序 */
    private Integer componentNo;

    /** 价格内唯一的业务组件代码 */
    private String componentCode;

    /** 组件类型 */
    private CommercialTermComponentComponentType componentType;

    /** 计费事件代码；开放目录，值域为 t_product_fee_event.fee_event_code */
    private String triggerCode;

    /** 计算基数 */
    private CalculationBase calculationBase;

    /** 退款时组件费用处理方式 */
    private RefundBehavior refundBehavior;

    /** 小数费率 0–1 */
    private BigDecimal rate;

    /** 固定金额参数 */
    private BigDecimal fixedAmount;

    /** 金额参数的 ISO 4217 币种；纯百分比无上下限时为空 */
    private String currency;

    /** 百分比结果下限 */
    private BigDecimal minAmount;

    /** 百分比结果上限 */
    private BigDecimal maxAmount;

    /** 仅 TIERED 非空的档位匹配基数 */
    private BandBasis bandBasis;

    /** 仅 TIERED 非空的档位计算方式 */
    private CommercialTermComponentBandMode bandMode;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
