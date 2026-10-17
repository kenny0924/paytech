package com.paytech.payment.auto.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.card.core.configuration.PaymentCardProperties;
import com.paytech.payment.card.core.crypto.CardCipher;
import com.paytech.payment.card.domain.CardInstrumentContext.CardInput;

/**
 * 卡数据加解密与摘要的纯 Java 契约（Payment Card 加密存储技术方案第 3 版第 2.1 节）。
 *
 * <p>
 * 覆盖：卡号、凭证、CVV 往返一致且同一明文两次密文不同；换记录 ID、换 DEK、篡改均解密失败；三类密文互不可替换；BIN、后四位、指纹与凭证摘要规则。卡号为公开测试卡号。
 */
class CardCipherTest {
    private static final long CARD_ID = 1001L;
    private static final long CREDENTIAL_ID = 2001L;
    private static final long INSTRUMENT_ID = 3001L;
    private static final String PAN = "4111111111111111";
    private static final String FINGERPRINT_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CardCipher cipher = new CardCipher(properties(FINGERPRINT_KEY));

    /** 卡号、凭证、CVV 往返一致，随机 IV 使同一明文两次密文不同。 */
    @Test
    void test_roundTrip() {
        byte[] dataKey = cipher.newDataKey();

        byte[] first = cipher.encryptPan(dataKey, CARD_ID, PAN);
        byte[] second = cipher.encryptPan(dataKey, CARD_ID, PAN);
        CardInput details =
            cipher.decryptDetails(dataKey, CREDENTIAL_ID, cipher.encryptDetails(dataKey, CREDENTIAL_ID, card(PAN)));

        assertAll(() -> assertEquals(CardCipher.DATA_KEY_BYTES, dataKey.length),
            () -> assertEquals(PAN, cipher.decryptPan(dataKey, CARD_ID, first)),
            () -> assertFalse(Arrays.equals(first, second)),
            () -> assertEquals("12", details.getExpiryMonth()), () -> assertEquals("2030", details.getExpiryYear()),
            () -> assertEquals("Test Cardholder", details.getCardholderName()), () -> assertNull(details.getPan()),
            () -> assertEquals("737", cipher.decryptSecurityCode(dataKey, INSTRUMENT_ID,
                cipher.encryptSecurityCode(dataKey, INSTRUMENT_ID, "737"))));
    }

    /** 换记录 ID（AAD）、换 DEK、篡改密文，都只报完整性失败。 */
    @Test
    void test_tamperRejected() {
        byte[] dataKey = cipher.newDataKey();
        byte[] encrypted = cipher.encryptPan(dataKey, CARD_ID, PAN);
        byte[] tampered = encrypted.clone();
        tampered[tampered.length - 1] ^= 1;

        assertAll(() -> assertThrows(ErrorException.class, () -> cipher.decryptPan(dataKey, CARD_ID + 1, encrypted)),
            () -> assertThrows(ErrorException.class, () -> cipher.decryptPan(cipher.newDataKey(), CARD_ID, encrypted)),
            () -> assertThrows(ErrorException.class, () -> cipher.decryptPan(dataKey, CARD_ID, tampered)),
            () -> assertThrows(ErrorException.class, () -> cipher.decryptPan(dataKey, CARD_ID, new byte[12])));
    }

    /** 卡号、凭证、CVV 使用不同 AAD：即使记录 ID 相同，密文也不能互换。 */
    @Test
    void test_ciphertextsNotInterchangeable() {
        byte[] dataKey = cipher.newDataKey();
        long sameId = 42L;
        byte[] pan = cipher.encryptPan(dataKey, sameId, PAN);
        byte[] details = cipher.encryptDetails(dataKey, sameId, card(PAN));
        byte[] securityCode = Base64.getDecoder().decode(cipher.encryptSecurityCode(dataKey, sameId, "737"));

        assertAll(() -> assertThrows(ErrorException.class, () -> cipher.decryptPan(dataKey, sameId, details)),
            () -> assertThrows(ErrorException.class, () -> cipher.decryptDetails(dataKey, sameId, pan)),
            () -> assertThrows(ErrorException.class, () -> cipher.decryptPan(dataKey, sameId, securityCode)));
    }

    /** BIN：16 位及以上取 8 位，否则 6 位；后四位；指纹同卡号稳定、不同密钥不同。 */
    @Test
    void test_cardAttributes() {
        byte[] other = new byte[32];
        Arrays.fill(other, (byte)1);
        var otherKey = new CardCipher(properties(Base64.getEncoder().encodeToString(other)));

        assertAll(() -> assertEquals("41111111", CardCipher.bin(PAN)),
            () -> assertEquals("378282", CardCipher.bin("378282246310005")),
            () -> assertEquals("0005", CardCipher.last4("378282246310005")),
            () -> assertTrue(cipher.fingerprint(PAN).matches("[0-9a-f]{64}")),
            () -> assertEquals(cipher.fingerprint(PAN), cipher.fingerprint(PAN)),
            () -> assertNotEquals(cipher.fingerprint(PAN), otherKey.fingerprint(PAN)));
    }

    /** 凭证摘要：持卡人大小写与空白不影响；换有效期、换卡号、换持卡人都改变摘要；与卡指纹不同。 */
    @Test
    void test_credentialDigest() {
        String digest = cipher.credentialDigest(card(PAN));
        CardInput spaced = card(PAN);
        spaced.setCardholderName("  TEST   cardholder ");
        CardInput otherYear = card(PAN);
        otherYear.setExpiryYear("2031");
        CardInput otherName = card(PAN);
        otherName.setCardholderName("Other Holder");

        assertAll(() -> assertTrue(digest.matches("[0-9a-f]{64}")),
            () -> assertEquals(digest, cipher.credentialDigest(spaced)),
            () -> assertNotEquals(digest, cipher.credentialDigest(otherYear)),
            () -> assertNotEquals(digest, cipher.credentialDigest(otherName)),
            () -> assertNotEquals(digest, cipher.credentialDigest(card("5555555555554444"))),
            () -> assertNotEquals(digest, cipher.fingerprint(PAN)),
            () -> assertEquals("TEST CARDHOLDER", CardCipher.normalizeCardholderName(" Test \t Cardholder ")));
    }

    /** 合成卡输入。 */
    static CardInput card(String pan) {
        CardInput card = new CardInput();
        card.setPan(pan);
        card.setExpiryMonth("12");
        card.setExpiryYear("2030");
        card.setCardholderName("Test Cardholder");
        card.setCvv("737");
        return card;
    }

    /** 只配置指纹密钥的属性。 */
    static PaymentCardProperties properties(String fingerprintKey) {
        PaymentCardProperties properties = new PaymentCardProperties();
        properties.setFingerprintKey(fingerprintKey);
        return properties;
    }
}
