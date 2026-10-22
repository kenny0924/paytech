package com.paytech.platform.support.api.referencedata.response;

import lombok.Getter;
import lombok.Setter;

/** 一个国家或地区所属的平台地理区域。 */
@Getter
@Setter
public class CountryRegionResponseBody {

    /**
     * 国家或地区代码
     * <li>ISO 3166-1 alpha-2，两位大写字母，例如 AE。</li>
     */
    private String countryCode;

    /**
     * 平台地理区域代码
     * <li>GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC 之一；GCC 为海湾合作委员会六国，MEA 为其余中东与非洲。</li>
     */
    private String regionCode;
}
