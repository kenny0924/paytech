package com.paytech.platform.support.referencedata.service.processor.response;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCountry;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块发布的参考数据国家结果；不暴露 DataObject。 */
@Getter
@AllArgsConstructor
public class ReferenceCountryResult {

    /** ISO 3166-1 alpha-2 代码。 */
    private final String countryCode;

    /** ISO 3166-1 alpha-3 代码。 */
    private final String alpha3Code;

    /** ISO 3166-1 三位数字代码。 */
    private final String numericCode;

    /** 英文短名。 */
    private final String name;

    /** 平台地理区域代码。 */
    private final String regionCode;

    /** 国际电话区号；没有为空。 */
    private final String callingCode;

    /** 法定货币代码；没有为空。 */
    private final String defaultCurrencyCode;

    /** 国旗文件地址；没有为空。 */
    private final String logoFileAddress;

    /** 国旗公开地址；没有国旗或文件不可公开访问为空。 */
    private final String logoPublicUrl;

    /** 状态。 */
    private final ReferenceDataStatus status;

    /** 乐观并发版本。 */
    private final int version;

    /** 由本模块 DataObject 与已换取的公开地址构造。 */
    public static ReferenceCountryResult of(ReferenceCountry row, String logoPublicUrl) {
        return new ReferenceCountryResult(row.getCountryCode(), row.getAlpha3Code(), row.getNumericCode(),
            row.getName(), row.getRegionCode(), row.getCallingCode(), row.getDefaultCurrencyCode(),
            row.getLogoFileAddress(), logoPublicUrl, row.getStatus(), row.getVersion());
    }
}
