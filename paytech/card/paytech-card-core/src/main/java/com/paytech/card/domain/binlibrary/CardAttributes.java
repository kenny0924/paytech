package com.paytech.card.domain.binlibrary;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次交易使用的 BIN 库卡属性快照：创建交易工具时查询一次，由 Payment 冻结（BIN 库需求 B4）
 * <li>未识别时 recognized=false、各属性为 null；没有生效版本时版本号也为 null。不含卡号或 BIN。</li>
 */
@Getter
@AllArgsConstructor
public class CardAttributes {
    /** 是否命中号段。 */
    private final boolean recognized;

    /** 查询时的生效版本号；没有生效版本时为 null。 */
    private final Integer binLibraryVersionNo;

    /** 命中号段的前缀位数。 */
    private final Integer matchedPrefixLength;

    /** 卡组织 Code。 */
    private final String cardBrand;

    /** 借贷记类型 Code。 */
    private final String fundingType;

    /** 卡等级。 */
    private final String cardLevel;

    /** 卡产品类别 Code。 */
    private final String cardProduct;

    /** 发卡国。 */
    private final String issuerCountry;

    /** 发卡区域。 */
    private final String issuerRegion;

    /**
     * 由匹配结果构造快照
     *
     * @param match 当前生效版本的匹配结果；没有生效版本时为 null
     * @return 快照；未命中时只保留版本号
     */
    public static CardAttributes of(CardBinMatchView match) {
        if (match == null) {
            return new CardAttributes(false, null, null, null, null, null, null, null, null);
        }
        if (!match.isRecognized()) {
            return new CardAttributes(false, match.getVersionNo(), null, null, null, null, null, null, null);
        }
        return new CardAttributes(true, match.getVersionNo(), match.getPrefixLength(), code(match.getCardBrand()),
            code(match.getFundingType()), match.getCardLevel(), code(match.getCardProduct()), match.getIssuerCountry(),
            match.getIssuerRegion());
    }

    /** 可空枚举的稳定代码。 */
    static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }
}
