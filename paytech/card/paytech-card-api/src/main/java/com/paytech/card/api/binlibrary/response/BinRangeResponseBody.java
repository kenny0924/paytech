package com.paytech.card.api.binlibrary.response;

import lombok.Getter;
import lombok.Setter;

/** 版本内的一个 BIN 号段；不含完整卡号 */
@Getter
@Setter
public class BinRangeResponseBody {
    /** 号段前缀，6～8 位数字 */
    private String binPrefix;

    /** 前缀位数：6、7 或 8 */
    private int prefixLength;

    /** 卡组织，{@code CardBrand} Code */
    private String cardBrand;

    /** 借贷记类型，{@code CardFundingType} Code；号段未提供为 null */
    private String fundingType;

    /** 卡等级，开放代码；号段未提供为 null */
    private String cardLevel;

    /** 卡产品类别，{@code CardProduct} Code；号段未提供为 null */
    private String cardProduct;

    /** 发卡国 ISO 3166-1 alpha-2 */
    private String issuerCountry;

    /** 发卡区域：GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC */
    private String issuerRegion;

    /** 发卡行名称，仅供运营查询展示；号段未提供为 null */
    private String issuerName;
}
