package com.paytech.card.domain.binlibrary;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;

import lombok.Getter;
import lombok.Setter;

/**
 * 按 BIN 最长前缀匹配的结果：BIN 库版本左关联命中号段（版本、号段两张表的组合查询）
 * <li>版本存在但没有命中号段时，号段字段全部为 null；没有所查版本时查询本身无结果。</li>
 */
@Getter
@Setter
public class CardBinMatchView {
    /** 所查询的版本号。 */
    private Integer versionNo;

    /** 所查询版本的状态。 */
    private BinLibraryVersionStatus versionStatus;

    /** 命中号段的前缀；未命中为 null。 */
    private String binPrefix;

    /** 命中号段的前缀位数；未命中为 null。 */
    private Integer prefixLength;

    /** 卡组织。 */
    private CardBrand cardBrand;

    /** 借贷记类型。 */
    private CardFundingType fundingType;

    /** 卡等级。 */
    private String cardLevel;

    /** 卡产品类别。 */
    private CardProduct cardProduct;

    /** 发卡国。 */
    private String issuerCountry;

    /** 发卡区域。 */
    private String issuerRegion;

    /** 发卡行名称。 */
    private String issuerName;

    /** 是否命中号段。 */
    public boolean isRecognized() {
        return binPrefix != null;
    }
}
