package com.paytech.tax.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_tax_calculation_line：计算行；追加写、不可变。 */
@Getter
@Setter
public class TaxCalculationLine {
    /** Snowflake 主键 */
    private Long id;

    /** 计算 ID */
    private Long calculationId;

    /** 源商品行引用 */
    private String sourceLineRef;

    /** 行号 */
    private Integer lineNo;

    /** 税类代码 */
    private String categoryCode;

    /** 非敏感商品说明；可空 */
    private String description;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 整行折扣 */
    private BigDecimal discountAmount;

    /** 行净额；可空 */
    private BigDecimal netAmount;

    /** 行税额；可空 */
    private BigDecimal chargedTaxAmount;

    /** 行含税金额；可空 */
    private BigDecimal grossAmount;

    /** 本行尾差 */
    private BigDecimal roundingDelta;

    /** 创建时间 */
    private Instant createTime;
}
