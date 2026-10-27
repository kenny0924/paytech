package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import lombok.Getter;
import lombok.Setter;

/** t_ref_country 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class ReferenceCountry {

    /**
     * 国家主键
     * <li>初始化数据使用 2001 起的固定 ID。</li>
     */
    private Long id;

    /** ISO 3166-1 alpha-2 国家或地区代码。 */
    private String countryCode;

    /** ISO 3166-1 alpha-3 代码。 */
    private String alpha3Code;

    /**
     * ISO 3166-1 三位数字代码
     * <li>保留前导零。</li>
     */
    private String numericCode;

    /** 英文短名。 */
    private String name;

    /**
     * 平台地理区域代码
     * <li>GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC；主数据目录值，按开放代码读取。</li>
     */
    private String regionCode;

    /**
     * 国际电话区号
     * <li>不含加号；没有区号的地区为空。</li>
     */
    private String callingCode;

    /**
     * 法定货币代码
     * <li>ISO 4217 代码，不要求存在于 t_ref_currency；没有法定货币时为空。</li>
     */
    private String defaultCurrencyCode;

    /**
     * 国旗文件地址
     * <li>文件服务地址 /files/v1/{fileId}；没有时为空。</li>
     */
    private String logoFileAddress;

    /** 状态。 */
    private ReferenceDataStatus status;

    /** 乐观并发版本，从 1 开始。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
