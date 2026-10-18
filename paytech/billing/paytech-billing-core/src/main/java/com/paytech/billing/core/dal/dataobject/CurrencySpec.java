package com.paytech.billing.core.dal.dataobject;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * t_currency_spec：全平台 ISO 4217 币种精度、舍入方式和启停状态的只读副本
 * <li>Owner 为 platform-support（D41），状态值类型取自 {@code paytech-platform-support-api}；本服务只读，过账与计费舍入不依赖远程调用。</li>
 */
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
    private CurrencySpecStatus status;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
