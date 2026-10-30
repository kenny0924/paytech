package com.paytech.tax.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一行计算结果；金额按币种精度补齐 */
@Getter
@Setter
public class TaxCalculationLineResponseBody {
    /** 计算行 ID */
    private String lineId;

    /** 源商品行引用（请求 lineRef 原值） */
    private String lineRef;

    /** 行号 */
    private Integer lineNo;

    /** 税类代码 */
    private String categoryCode;

    /** 非敏感商品说明；请求未提供时为空 */
    private String description;

    /** 数量 */
    private String quantity;

    /** 单价 */
    private String unitPrice;

    /** 整行折扣 */
    private String discountAmount;

    /** 行净额（INCLUSIVE 时为反算结果；INCLUSIVE 且 BLOCKED / NOT_MANAGED 时为空） */
    private String netAmount;

    /** 行税额；BLOCKED 与 NOT_MANAGED 为空 */
    private String taxAmount;

    /** 行含税金额 = netAmount + taxAmount；EXCLUSIVE 且 BLOCKED / NOT_MANAGED 时为空 */
    private String grossAmount;

    /**
     * 本行取整尾差 = 取整后税额 − 未取整税额
     * <li>八位小数；BLOCKED 与 NOT_MANAGED 为 0。</li>
     */
    private String roundingDelta;

    /** 本行税组件；BLOCKED 与 NOT_MANAGED 为空列表 */
    private List<TaxComponentResponseBody> components;
}
