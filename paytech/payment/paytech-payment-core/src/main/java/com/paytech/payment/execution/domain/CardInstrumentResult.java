package com.paytech.payment.execution.domain;

import java.time.Instant;

/**
 * card-service 返回的卡交易工具
 *
 * @param instrumentRef Card 工具引用
 * @param expiresAt 工具实际期限
 * @param cardBin 卡 BIN（风控输入）
 * @param cardFingerprint 卡指纹（受限值，风控输入）
 * @param cardAttributes BIN 库卡属性快照，Payment 只在首次绑定 Token 时冻结
 */
public record CardInstrumentResult(String instrumentRef, Instant expiresAt, String cardBin, String cardFingerprint,
    CardAttributes cardAttributes) {
    @Override
    public String toString() {
        return "CardInstrumentResult[" + instrumentRef + "]";
    }

    /**
     * BIN 库卡属性快照（BIN 库需求 technique 3.4）
     * <li>未识别时各属性为空；有生效版本时版本号仍有值，没有生效版本时版本号也为空。</li>
     *
     * @param cardBrand 卡组织 Code
     * @param fundingType 借贷记类型 Code
     * @param cardLevel 卡等级（开放代码）
     * @param cardProduct 卡产品类别 Code
     * @param issuerCountry 发卡国 ISO 3166-1 alpha-2
     * @param issuerRegion 发卡区域
     * @param binLibraryVersionNo 查询时的 BIN 库生效版本号
     * @param matchedPrefixLength 命中号段的前缀位数（6、7、8）
     */
    public record CardAttributes(String cardBrand, String fundingType, String cardLevel, String cardProduct,
        String issuerCountry, String issuerRegion, Integer binLibraryVersionNo, Integer matchedPrefixLength) {
    }
}
