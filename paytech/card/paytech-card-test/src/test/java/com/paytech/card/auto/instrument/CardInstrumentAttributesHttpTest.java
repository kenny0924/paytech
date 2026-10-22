package com.paytech.card.auto.instrument;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.card.api.enums.CardProduct;
import com.paytech.card.api.instrument.CardInstrumentApi;
import com.paytech.card.test.base.AbstractBinLibraryTest;

/**
 * 创建卡交易工具响应中的 BIN 库卡属性快照（BIN 库需求 R4、B4）：经 card 真实 HTTP 入口，按当前生效版本一条 SQL 做最长前缀匹配，不缓存。
 *
 * <p>
 * 覆盖：16 位卡取 8 位 BIN 命中 8 位号段（AC-03）；15 位卡只有 6 位 BIN；未识别时工具照常创建、属性为空但带版本号（AC-04）；没有生效版本时版本号也为空；
 * 激活新版本后新工具使用新版本属性（AC-02 的 Card 侧）；快照字段完整（AC-07）。
 *
 * @see CardInstrumentApi#createInstrument(String, com.paytech.card.api.instrument.request.CreateCardInstrumentRequestBody)
 */
class CardInstrumentAttributesHttpTest extends AbstractBinLibraryTest {
    /** 公开测试卡号 4012888888881881：8 位 BIN 40128888。 */
    private static final String EIGHT_DIGIT_PAN = "4012888888881881";

    /** 15 位公开测试卡号（Amex），BIN 只有 6 位。 */
    private static final String SHORT_PAN = "378282246310005";

    /** 公开测试卡号（Mastercard），BIN 51051051 不在固件号段内。 */
    private static final String UNKNOWN_PAN = "5105105105105100";

    /** 8 位号段优先于同前缀的 6 位号段；快照含版本号、前缀位数与全部属性。 */
    @Test
    void test_eightDigitRangeWins() {
        seedActiveVersion();

        var attributes = attributes(EIGHT_DIGIT_PAN);

        assertEquals(Map.of("recognized", true, "binLibraryVersionNo", 1, "matchedPrefixLength", 8, "cardBrand",
            CardBrand.VISA.getCode(), "fundingType", CardFundingType.DEBIT.getCode(), "cardLevel", "CLASSIC", "cardProduct",
            CardProduct.CONSUMER.getCode(), "issuerCountry", "GB", "issuerRegion", "EUROPE"), attributes);
    }

    /** 15 位卡只有 6 位 BIN，只能命中 6 位号段；号段未提供的卡等级为 null。 */
    @Test
    void test_sixDigitBin() {
        seedActiveVersion();

        var attributes = attributes(SHORT_PAN);

        assertAll(() -> assertEquals(true, attributes.get("recognized")), () -> assertEquals(6, attributes.get("matchedPrefixLength")),
            () -> assertEquals(CardBrand.AMEX.getCode(), attributes.get("cardBrand")),
            () -> assertEquals(CardFundingType.CHARGE.getCode(), attributes.get("fundingType")),
            () -> assertNull(attributes.get("cardLevel")), () -> assertEquals("NORTH_AMERICA", attributes.get("issuerRegion")));
    }

    /** 未识别：工具照常创建，属性为空，版本号为当前生效版本。 */
    @Test
    void test_unrecognizedBin() {
        seedActiveVersion();

        var created = createInstrument(instrumentBody(submission(), card(UNKNOWN_PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false));

        @SuppressWarnings("unchecked")
        var attributes = (Map<String, Object>)created.get("cardAttributes");
        assertAll(() -> assertNotNull(created.get("instrumentRef")), () -> assertEquals(false, attributes.get("recognized")),
            () -> assertEquals(1, attributes.get("binLibraryVersionNo")), () -> assertNull(attributes.get("matchedPrefixLength")),
            () -> assertNull(attributes.get("cardBrand")), () -> assertNull(attributes.get("issuerCountry")),
            () -> assertEquals(1, mapper.cardInstruments().size()));
    }

    /** 没有生效版本：未识别且版本号为空。 */
    @Test
    void test_noActiveVersion() {
        binLibrary.version(1, BinLibraryVersionStatus.VALID, 0);

        var attributes = attributes(PAN);

        assertAll(() -> assertEquals(false, attributes.get("recognized")), () -> assertNull(attributes.get("binLibraryVersionNo")));
    }

    /** 激活新版本后，新工具使用新版本的属性。 */
    @Test
    void test_newVersionAppliesToNewInstruments() {
        seedActiveVersion();
        var before = attributes(PAN);
        long v2 = binLibrary.version(2, BinLibraryVersionStatus.VALID, 1);
        binLibrary.range(v2, "411111", CardBrand.MASTERCARD, "AE", "GCC");
        assertEquals(200, activate(2, 1).status());

        var after = attributes(PAN);

        assertAll(() -> assertEquals(CardBrand.VISA.getCode(), before.get("cardBrand")), () -> assertEquals(1, before.get("binLibraryVersionNo")),
            () -> assertEquals(CardBrand.MASTERCARD.getCode(), after.get("cardBrand")), () -> assertEquals(2, after.get("binLibraryVersionNo")),
            () -> assertEquals("GCC", after.get("issuerRegion")));
    }

    /** 以新来源提交为给定卡号创建工具，返回卡属性快照。 */
    @SuppressWarnings("unchecked")
    Map<String, Object> attributes(String pan) {
        return (Map<String, Object>)createInstrument(instrumentBody(submission(), card(pan, EXPIRY_YEAR, CARDHOLDER_NAME), null,
            false)).get("cardAttributes");
    }
}
