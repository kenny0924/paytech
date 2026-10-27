package com.paytech.platform.support.referencedata.service.processor.response;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块发布的参考数据币种结果；不暴露 DataObject。 */
@Getter
@AllArgsConstructor
public class ReferenceCurrencyResult {

    /** ISO 4217 代码。 */
    private final String currencyCode;

    /** ISO 4217 三位数字代码。 */
    private final String numericCode;

    /** 英文名称。 */
    private final String name;

    /** 货币符号。 */
    private final String symbol;

    /** 最小货币单位位数。 */
    private final int minorUnit;

    /** 舍入方式。 */
    private final CurrencyRoundingMode roundingMode;

    /** logo 文件地址；没有为空。 */
    private final String logoFileAddress;

    /** logo 公开地址；没有 logo 或文件不可公开访问为空。 */
    private final String logoPublicUrl;

    /** 状态。 */
    private final CurrencySpecStatus status;

    /** 乐观并发版本。 */
    private final int version;

    /** 由本模块 DataObject 与已换取的公开地址构造。 */
    public static ReferenceCurrencyResult of(ReferenceCurrency row, String logoPublicUrl) {
        return new ReferenceCurrencyResult(row.getCurrencyCode(), row.getNumericCode(), row.getName(), row.getSymbol(),
            row.getMinorUnit(), row.getRoundingMode(), row.getLogoFileAddress(), logoPublicUrl, row.getStatus(),
            row.getVersion());
    }
}
