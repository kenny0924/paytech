package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.core.enums.ReferenceDataStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_currency_spec：保存 Finance 使用的 ISO 4217 币种精度、舍入方式和启停状态。 */
@Getter
@Setter
public class CurrencySpec {
    /**
     * 服务侧生成的币种规则 Snowflake 主键
     */
    private Long id;

    /**
     * ISO 4217 三位币种代码
     * <li>取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 记账与结算金额允许保留的小数位数，取值范围为零至八
     */
    private Integer minorUnit;

    /**
     * 金额从八位计算精度归整到币种精度时采用的固定方式
     * <li>允许值：HALF_UP。</li>
     */
    private String roundingMode;

    /**
     * 币种规则当前是否允许被新业务使用
     * <li>允许值：DRAFT、ACTIVE、INACTIVE。</li>
     */
    private ReferenceDataStatus status;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
