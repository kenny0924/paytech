package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_country_region 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class CountryRegion {

    /**
     * 国家区域映射主键
     * <li>初始化数据使用 2001 起的固定 ID。</li>
     */
    private Long id;

    /** ISO 3166-1 alpha-2 国家或地区代码。 */
    private String countryCode;

    /**
     * 平台地理区域代码
     * <li>GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC；主数据目录值，按开放代码读取。</li>
     */
    private String regionCode;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
