package com.paytech.commercial.api.price.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 价格的一个计算组件；TIERED 时档位整表返回，由调用方按自身基数选档。 */
@Getter
@Setter
public class CommercialPriceComponentResponseBody {
    /** 组件 ID */
    private String componentId;

    /** 组件序号，从 1 连续 */
    private Integer componentNo;

    /** 组件代码 */
    private String componentCode;

    /** 组件类型：PERCENTAGE、FIXED、TIERED */
    private String componentType;

    /** 触发计费事件 */
    private String triggerCode;

    /** 计算基数 */
    private String calculationBase;

    /** 退款时费用处理 */
    private String refundBehavior;

    /** 费率 */
    private String rate;

    /** 固定金额 */
    private String fixedAmount;

    /** 金额币种 */
    private String currency;

    /** 最低金额 */
    private String minAmount;

    /** 最高金额 */
    private String maxAmount;

    /** 阶梯基数 */
    private String bandBasis;

    /** 阶梯模式 */
    private String bandMode;

    /** 档位，按 bandNo 排序；非 TIERED 为空列表 */
    private List<CommercialPriceBandResponseBody> bands;

    /**
     * 与来源模板同组件的比较（破标）
     * <li>只在固定版本价格全集中给出；价格没有来源模板时为 NO_BENCHMARK，命中结果为 null。</li>
     */
    private CommercialPriceBenchmarkResponseBody benchmark;
}
