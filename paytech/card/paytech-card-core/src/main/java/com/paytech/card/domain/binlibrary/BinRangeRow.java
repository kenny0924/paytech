package com.paytech.card.domain.binlibrary;

import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 导入文件中校验通过的一行号段，发卡区域已按国家区域映射推导。 */
@Getter
@AllArgsConstructor
public class BinRangeRow {
    /** 号段前缀，6～8 位数字。 */
    private final String binPrefix;

    /** 卡组织。 */
    private final CardBrand cardBrand;

    /** 借贷记类型；未提供为 null。 */
    private final CardFundingType fundingType;

    /** 卡等级；未提供为 null。 */
    private final String cardLevel;

    /** 卡产品类别；未提供为 null。 */
    private final CardProduct cardProduct;

    /** 发卡国。 */
    private final String issuerCountry;

    /** 发卡区域。 */
    private final String issuerRegion;

    /** 发卡行名称（已去除首尾空白）；未提供为 null。 */
    private final String issuerName;
}
