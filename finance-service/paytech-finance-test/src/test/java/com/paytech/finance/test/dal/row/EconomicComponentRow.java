package com.paytech.finance.test.dal.row;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 测试夹具直接插入的 t_transaction_economic_component 行；只承载引擎读取的类型、归属与金额及必填来源列。 */
@Data
public class EconomicComponentRow {
    private Long id;
    private Long transactionEconomicsId;
    private Integer componentNo;
    private String componentType;
    private String partyType;
    private String partyId;
    private BigDecimal basisAmount;
    private BigDecimal rawAmount;
    private BigDecimal postedAmount;
    private String effectDirection;
    private String sourceRuleType;
    private Long sourceRuleVersionId;
    private String sourceComponentCode;
    private String sourceTriggerCode;
    private String sourceCalculationBase;
    private String calculationMethod;
    private String roundingMode;
    private Instant createTime;
}
