package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 一条币种规则；字段与 t_currency_spec 的业务列一致。 */
@Getter
@Setter
public class CurrencySpecResponseBody {

    /**
     * ISO 4217 三位币种代码
     * <li>大写字母，例如 USD。</li>
     */
    private String currency;

    /**
     * 最小货币单位位数
     * <li>金额允许保留的小数位数，0..8；USD 为 2。</li>
     */
    private int minorUnit;

    /**
     * 舍入方式
     * <li>{@code CurrencyRoundingMode} Code，当前只有 HALF_UP。</li>
     */
    private String roundingMode;

    /**
     * 状态
     * <li>{@code CurrencySpecStatus} Code：DRAFT、ACTIVE、INACTIVE；只有 ACTIVE 允许新业务使用。</li>
     */
    private String status;

    /**
     * 乐观并发版本
     * <li>正整数；维护接口按此版本校验并发修改。</li>
     */
    private int version;

    /**
     * 最后更新时间
     * <li>UTC ISO-8601，微秒精度。</li>
     */
    private String updateTime;
}
