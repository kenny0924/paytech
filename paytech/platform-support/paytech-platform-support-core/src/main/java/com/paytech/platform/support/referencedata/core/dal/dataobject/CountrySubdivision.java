package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import lombok.Getter;
import lombok.Setter;

/** t_ref_country_subdivision 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class CountrySubdivision {

    /**
     * 行政区主键
     * <li>初始化数据使用 3001 起的固定 ID。</li>
     */
    private Long id;

    /** ISO 3166-2 行政区代码，例如 AE-DU。 */
    private String subdivisionCode;

    /** 所属国家 ISO 3166-1 alpha-2 代码。 */
    private String countryCode;

    /**
     * 上级行政区代码
     * <li>一级行政区为空。</li>
     */
    private String parentSubdivisionCode;

    /**
     * 行政区类别
     * <li>ISO 3166-2 定义的类别，如 EMIRATE、REGION、GOVERNORATE；开放代码。</li>
     */
    private String category;

    /** ISO 3166-2 拉丁文名称。 */
    private String name;

    /**
     * 本地文字名称
     * <li>没有时为空。</li>
     */
    private String localName;

    /** 状态。 */
    private ReferenceDataStatus status;

    /** 乐观并发版本，从 1 开始。 */
    private Integer version;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
