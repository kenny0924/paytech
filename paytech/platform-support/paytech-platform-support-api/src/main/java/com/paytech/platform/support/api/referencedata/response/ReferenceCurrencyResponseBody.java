package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 参考数据目录中的一个币种；字段与 t_ref_currency 的业务列一致。 */
@Getter
@Setter
public class ReferenceCurrencyResponseBody {

    /**
     * ISO 4217 三位币种代码
     * <li>大写字母，例如 USD。</li>
     */
    private String currencyCode;

    /**
     * ISO 4217 三位数字代码
     * <li>保留前导零，例如 USD 为 840、AED 为 784。</li>
     */
    private String numericCode;

    /**
     * 英文名称
     * <li>例如 US Dollar；最长 128 字符。</li>
     */
    private String name;

    /**
     * 货币符号
     * <li>例如 $；没有专用符号时为币种代码；最长 8 字符。</li>
     */
    private String symbol;

    /**
     * 最小货币单位位数
     * <li>金额允许保留的小数位数，0..8；USD 为 2。</li>
     */
    private Integer minorUnit;

    /**
     * 舍入方式
     * <li>{@code CurrencyRoundingMode} Code，当前只有 HALF_UP。</li>
     */
    private String roundingMode;

    /**
     * logo 文件地址
     * <li>文件服务地址 {@code /files/v1/{fileId}}；没有 logo 时为空。</li>
     */
    private String logoFileAddress;

    /**
     * logo 公开访问地址
     * <li>由 Platform 按文件地址批量换取；没有 logo、文件不是公开文件或检查未通过时为空，调用方显示占位。</li>
     */
    private String logoPublicUrl;

    /**
     * 状态
     * <li>{@code ReferenceDataStatus} Code：DRAFT、ACTIVE、INACTIVE；只有 ACTIVE 允许被新业务选择。</li>
     */
    private String status;

    /**
     * 乐观并发版本
     * <li>正整数，从 1 开始。</li>
     */
    private Integer version;
}
