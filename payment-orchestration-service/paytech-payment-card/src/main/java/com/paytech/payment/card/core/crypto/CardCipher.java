package com.paytech.payment.card.core.crypto;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.card.core.configuration.PaymentCardProperties;
import com.paytech.payment.card.domain.CardInstrumentContext.CardInput;

/**
 * 卡数据的本地密码学运算：生成本卡 DEK，AES-256-GCM 加解密卡号、卡凭证与 CVV，HMAC-SHA256 卡指纹与凭证摘要。
 *
 * <p>
 * 纯本地计算，不访问数据库或外部依赖。密文格式为 {@code IV(12) ‖ 密文 ‖ Tag(16)}；卡号、凭证、CVV 的 AAD 分别绑定卡、卡凭证、卡交易工具的 ID，
 * 三类密文互相不能替换，也不能挪到别的记录上。卡指纹与凭证摘要共用指纹密钥，以不同输入前缀区分用途。异常信息不含卡值或密钥。
 */
@Component
public class CardCipher {
    /** DEK 字节数（AES-256）。 */
    public static final int DATA_KEY_BYTES = 32;

    /** 卡号不少于该位数时 BIN 取 8 位，否则取 6 位。 */
    static final int LONG_PAN_LENGTH = 16;

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** 明文编码版本，变更编码时递增。 */
    private static final byte PAYLOAD_VERSION = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] fingerprintKey;

    /** 启动时解码指纹密钥；配置无效时由属性校验抛出。 */
    public CardCipher(PaymentCardProperties properties) {
        this.fingerprintKey = properties.fingerprintKeyBytes();
    }

    /** 生成一把新的本卡 DEK；调用方用完后清零。 */
    public byte[] newDataKey() {
        byte[] key = new byte[DATA_KEY_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }

    /** 用本卡 DEK 加密卡号，AAD 绑定卡 ID。 */
    public byte[] encryptPan(byte[] dataKey, long paymentCardId, String pan) {
        return encrypt(dataKey, panAad(paymentCardId), encode(pan));
    }

    /** 解密卡号；密文被篡改、AAD 或 DEK 不匹配时失败。 */
    public String decryptPan(byte[] dataKey, long paymentCardId, byte[] encryptedPan) {
        return decode(decrypt(dataKey, panAad(paymentCardId), encryptedPan), 1)[0];
    }

    /** 用本卡 DEK 加密有效期与持卡人原值，AAD 绑定卡凭证 ID。 */
    public byte[] encryptDetails(byte[] dataKey, long paymentCardCredentialId, CardInput card) {
        return encrypt(dataKey, detailsAad(paymentCardCredentialId),
            encode(card.getExpiryMonth(), card.getExpiryYear(), card.getCardholderName()));
    }

    /**
     * 解密有效期与持卡人
     *
     * @return 只含有效期与持卡人的卡输入
     */
    public CardInput decryptDetails(byte[] dataKey, long paymentCardCredentialId, byte[] encryptedDetails) {
        String[] fields = decode(decrypt(dataKey, detailsAad(paymentCardCredentialId), encryptedDetails), 3);
        CardInput card = new CardInput();
        card.setExpiryMonth(fields[0]);
        card.setExpiryYear(fields[1]);
        card.setCardholderName(fields[2]);
        return card;
    }

    /** 用本卡 DEK 加密 CVV，AAD 绑定卡交易工具 ID，返回 Base64 文本供 Redis 暂存。 */
    public String encryptSecurityCode(byte[] dataKey, long paymentCardInstrumentId, String securityCode) {
        byte[] encrypted = encrypt(dataKey, securityCodeAad(paymentCardInstrumentId),
            securityCode.getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /** 解密 Redis 中暂存的 CVV；内容被篡改或 DEK 不匹配时失败。 */
    public String decryptSecurityCode(byte[] dataKey, long paymentCardInstrumentId, String encryptedSecurityCode) {
        byte[] encrypted;
        try {
            encrypted = Base64.getDecoder().decode(encryptedSecurityCode);
        } catch (IllegalArgumentException invalid) {
            throw new ErrorException("卡安全码暂存内容无效");
        }
        return new String(decrypt(dataKey, securityCodeAad(paymentCardInstrumentId), encrypted),
            StandardCharsets.US_ASCII);
    }

    /** 卡指纹：对卡号做 HMAC-SHA256 的小写十六进制；同一卡号恒定，不能反推卡号。 */
    public String fingerprint(String pan) {
        return hmac(pan);
    }

    /**
     * 凭证摘要：对卡号、有效期与规范化持卡人做 HMAC-SHA256 的小写十六进制
     * <li>带卡号计算：两张卡的有效期与持卡人恰好相同时摘要也不同，不暴露二者关联。</li>
     * <li>持卡人只有大小写或空白差异时摘要相同。</li>
     */
    public String credentialDigest(CardInput card) {
        return hmac("credential|" + card.getPan() + "|" + card.getExpiryMonth() + "|" + card.getExpiryYear() + "|"
            + normalizeCardholderName(card.getCardholderName()));
    }

    /** BIN：卡号不少于 16 位取前 8 位，否则取前 6 位。 */
    public static String bin(String pan) {
        return pan.substring(0, pan.length() >= LONG_PAN_LENGTH ? 8 : 6);
    }

    /** 卡号后 4 位。 */
    public static String last4(String pan) {
        return pan.substring(pan.length() - 4);
    }

    /** 持卡人规范化：去首尾空白、连续空白合并为一个空格、转大写；只用于凭证摘要。 */
    public static String normalizeCardholderName(String cardholderName) {
        return cardholderName.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** 以指纹密钥计算 HMAC-SHA256 小写十六进制。 */
    String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(fingerprintKey, MAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new ErrorException("卡指纹计算不可用", unavailable);
        }
    }

    /** 随机 IV 加密，输出 IV ‖ 密文 ‖ Tag。 */
    byte[] encrypt(byte[] dataKey, byte[] aad, byte[] plaintext) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, KEY_ALGORITHM),
                new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] sealed = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException unavailable) {
            throw new ErrorException("卡数据加密不可用", unavailable);
        }
    }

    /** 拆出 IV 后解密并校验 Tag；任何不匹配都只报完整性失败，不带原因细节。 */
    byte[] decrypt(byte[] dataKey, byte[] aad, byte[] encrypted) {
        if (encrypted == null || encrypted.length <= IV_BYTES) {
            throw new ErrorException("卡数据完整性校验失败");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, KEY_ALGORITHM),
                new GCMParameterSpec(TAG_BITS, encrypted, 0, IV_BYTES));
            cipher.updateAAD(aad);
            return cipher.doFinal(encrypted, IV_BYTES, encrypted.length - IV_BYTES);
        } catch (GeneralSecurityException failed) {
            throw new ErrorException("卡数据完整性校验失败");
        }
    }

    /** 卡号 AAD。 */
    static byte[] panAad(long paymentCardId) {
        return ("payment-card:" + paymentCardId).getBytes(StandardCharsets.US_ASCII);
    }

    /** 卡凭证 AAD。 */
    static byte[] detailsAad(long paymentCardCredentialId) {
        return ("payment-card-credential:" + paymentCardCredentialId).getBytes(StandardCharsets.US_ASCII);
    }

    /** CVV AAD。 */
    static byte[] securityCodeAad(long paymentCardInstrumentId) {
        return ("payment-card-instrument:" + paymentCardInstrumentId + ":cvv").getBytes(StandardCharsets.US_ASCII);
    }

    /** 版本号加长度前缀字段的固定编码。 */
    static byte[] encode(String... fields) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeByte(PAYLOAD_VERSION);
            for (String field : fields) {
                out.writeUTF(field);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new ErrorException("卡数据编码失败", impossible);
        }
    }

    /** 解码固定字段数的明文；版本不符或结构损坏时失败。 */
    static String[] decode(byte[] payload, int fieldCount) {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readByte() != PAYLOAD_VERSION) {
                throw new ErrorException("卡数据编码版本不支持");
            }
            String[] fields = new String[fieldCount];
            for (int i = 0; i < fieldCount; i++) {
                fields[i] = in.readUTF();
            }
            if (in.available() != 0) {
                throw new ErrorException("卡数据编码结构无效");
            }
            return fields;
        } catch (IOException damaged) {
            throw new ErrorException("卡数据编码结构无效");
        }
    }
}
