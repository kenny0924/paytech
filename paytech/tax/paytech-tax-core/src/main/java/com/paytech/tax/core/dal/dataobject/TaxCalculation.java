package com.paytech.tax.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxPriceMode;

import lombok.Getter;
import lombok.Setter;

/** t_tax_calculation：一次不可变的税务计算头。 */
@Getter
@Setter
public class TaxCalculation {
    /** Snowflake 主键，对外 calculationId */
    private Long id;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源对象类型：ORDER */
    private String sourceObjectType;

    /** 来源对象 ID */
    private Long sourceObjectId;

    /** 来源对象公开编号 */
    private String sourceObjectNo;

    /** 准备版本 */
    private Integer sourceRevision;

    /** 税务范围：SALE */
    private String taxScopeKey;

    /** 计算阶段 */
    private TaxCalculationStage calculationStage;

    /** 供应类型：SALE */
    private String supplyKind;

    /** 商业模式代码 */
    private String businessMode;

    /** 卖方 Party ID；可空 */
    private Long sellerPartyId;

    /** 买方 Client ID */
    private Long buyerClientId;

    /** 纳税主体 Party ID；调用方未确定责任方时为空 */
    private Long taxSubjectPartyId;

    /** 收单 OE 编号 */
    private String acquiringEntityNo;

    /** 币种 */
    private String currency;

    /** 价格口径 */
    private TaxPriceMode priceMode;

    /** 候选税点日期 */
    private LocalDate taxPointDate;

    /** 税点依据 */
    private String taxPointBasis;

    /** 结果状态 */
    private TaxCalculationStatus status;

    /** 税额来源 */
    private TaxAmountSource taxAmountSource;

    /** BLOCKED 原因 JSON 数组；其他状态为空 */
    private String blockReasonsJson;

    /** 净额合计；可空 */
    private BigDecimal netAmount;

    /** 税额合计；BLOCKED 为空 */
    private BigDecimal chargedTaxAmount;

    /** 总额；可空 */
    private BigDecimal grossAmount;

    /** 头级尾差；BLOCKED 为空 */
    private BigDecimal roundingAmount;

    /** 报价到期；只有 FINAL 且 READY 非空 */
    private Instant expiresAt;

    /** 创建时间 */
    private Instant createTime;
}
