package com.paytech.card.api.binlibrary.response;

import lombok.Getter;
import lombok.Setter;

/** 单个 BIN 的卡属性查询结果；不含完整卡号 */
@Getter
@Setter
public class BinLookupResponseBody {
    /** 是否命中号段；false 时各属性为 null */
    private boolean recognized;

    /** 所查询的版本号；没有生效版本时为 null */
    private Integer binLibraryVersionNo;

    /** 命中号段的前缀，6～8 位；未命中为 null */
    private String binPrefix;

    /** 命中号段的前缀位数：6、7 或 8；未命中为 null */
    private Integer matchedPrefixLength;

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
