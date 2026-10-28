package com.paytech.card.domain.binlibrary;

import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 号段列表的组合筛选条件；各项为 null 时不限制，计数与分页使用同一组条件。 */
@Getter
@AllArgsConstructor
public class BinRangeFilter {
    /**
     * 号段前缀的开头
     * <li>1～8 位数字（入口已校验，不含 LIKE 通配符）；null 不限制。</li>
     */
    private final String binPrefix;

    /** 卡组织；null 不限制。 */
    private final CardBrand cardBrand;

    /** 借贷记类型；null 不限制，指定时不匹配未提供借贷记的号段。 */
    private final CardFundingType fundingType;

    /** 发卡国 ISO 3166-1 alpha-2；null 不限制。 */
    private final String issuerCountry;
}
