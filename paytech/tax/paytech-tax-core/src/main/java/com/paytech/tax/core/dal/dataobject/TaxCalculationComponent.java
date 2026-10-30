package com.paytech.tax.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.enums.LiableRole;
import com.paytech.tax.core.enums.TaxCalculationMethod;

import lombok.Getter;
import lombok.Setter;

/** t_tax_calculation_component：一行上的一个税组件；追加写、不可变。 */
@Getter
@Setter
public class TaxCalculationComponent {
    /** Snowflake 主键 */
    private Long id;

    /** 计算 ID */
    private Long calculationId;

    /** 计算行 ID */
    private Long lineId;

    /** 组件代码 */
    private String componentCode;

    /** 征税辖区 */
    private String jurisdictionCode;

    /** 税种 */
    private String taxType;

    /** 处理类型 */
    private TaxTreatment treatment;

    /** 原因码 */
    private String reasonCode;

    /** 承担角色 */
    private LiableRole liableRole;

    /** 准入 ID */
    private Long marketId;

    /** 注册 ID；可空 */
    private Long registrationId;

    /** 地区包 ID；可空 */
    private Long policyId;

    /** 规则代码；可空 */
    private String ruleCode;

    /** 税基 */
    private BigDecimal taxBase;

    /** 税率；无适用税率为空 */
    private BigDecimal rate;

    /** 税额 */
    private BigDecimal chargedTaxAmount;

    /** 算法 */
    private TaxCalculationMethod calculationMethod;

    /** 取整方法 */
    private String roundingMethod;

    /** 取整位数 */
    private Integer roundingScale;

    /** 创建时间 */
    private Instant createTime;
}
