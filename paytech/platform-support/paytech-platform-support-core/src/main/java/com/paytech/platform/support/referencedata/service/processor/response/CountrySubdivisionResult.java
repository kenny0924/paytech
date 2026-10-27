package com.paytech.platform.support.referencedata.service.processor.response;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CountrySubdivision;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块发布的行政区结果；不暴露 DataObject。 */
@Getter
@AllArgsConstructor
public class CountrySubdivisionResult {

    /** ISO 3166-2 行政区代码。 */
    private final String subdivisionCode;

    /** 所属国家代码。 */
    private final String countryCode;

    /** 上级行政区代码；一级行政区为空。 */
    private final String parentSubdivisionCode;

    /** ISO 3166-2 行政区类别。 */
    private final String category;

    /** 拉丁文名称。 */
    private final String name;

    /** 本地文字名称；没有为空。 */
    private final String localName;

    /** 状态。 */
    private final ReferenceDataStatus status;

    /** 乐观并发版本。 */
    private final int version;

    /** 由本模块 DataObject 构造。 */
    public static CountrySubdivisionResult of(CountrySubdivision row) {
        return new CountrySubdivisionResult(row.getSubdivisionCode(), row.getCountryCode(),
            row.getParentSubdivisionCode(), row.getCategory(), row.getName(), row.getLocalName(), row.getStatus(),
            row.getVersion());
    }
}
