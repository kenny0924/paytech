package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 参考数据目录中的一个行政区；字段与 t_ref_country_subdivision 的业务列一致。 */
@Getter
@Setter
public class CountrySubdivisionResponseBody {

    /**
     * 行政区代码
     * <li>ISO 3166-2，例如 AE-DU、SA-01；最长 6 字符。</li>
     */
    private String subdivisionCode;

    /**
     * 所属国家代码
     * <li>ISO 3166-1 alpha-2，两位大写字母。</li>
     */
    private String countryCode;

    /**
     * 上级行政区代码
     * <li>一级行政区为空。</li>
     */
    private String parentSubdivisionCode;

    /**
     * 行政区类别
     * <li>ISO 3166-2 定义的类别，如 EMIRATE、REGION、GOVERNORATE；非固定枚举。</li>
     */
    private String category;

    /**
     * 英文名称
     * <li>例如 Dubai。</li>
     */
    private String name;

    /**
     * 当地语言名称
     * <li>ISO 公布的当地文字名称（AE、SA、EG 为阿拉伯文）；没有时为空。</li>
     */
    private String localName;

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
