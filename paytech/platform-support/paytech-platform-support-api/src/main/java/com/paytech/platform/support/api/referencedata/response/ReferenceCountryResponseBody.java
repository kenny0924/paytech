package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 参考数据目录中的一个国家或地区；字段与 t_ref_country 的业务列一致。 */
@Getter
@Setter
public class ReferenceCountryResponseBody {

    /**
     * 国家或地区代码
     * <li>ISO 3166-1 alpha-2，两位大写字母，例如 AE。</li>
     */
    private String countryCode;

    /**
     * 三位字母代码
     * <li>ISO 3166-1 alpha-3，例如 ARE。</li>
     */
    private String alpha3Code;

    /**
     * 三位数字代码
     * <li>ISO 3166-1 numeric，保留前导零，例如 784。</li>
     */
    private String numericCode;

    /**
     * 英文名称
     * <li>ISO 3166-1 英文短名，例如 United Arab Emirates。</li>
     */
    private String name;

    /**
     * 平台地理区域代码
     * <li>GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC 之一。</li>
     */
    private String regionCode;

    /**
     * 国际电话区号
     * <li>不含加号，例如 971；没有区号的地区（如南极洲）为空。</li>
     */
    private String callingCode;

    /**
     * 法定货币代码
     * <li>ISO 4217 三位币种代码，例如 AED；没有法定货币时为空。</li>
     */
    private String defaultCurrencyCode;

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
