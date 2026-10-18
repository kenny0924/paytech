package com.paytech.foundation.token;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 生成无业务前缀的公开随机 Token 主体；混入值不承担身份绑定或认证职责。 */
public final class PublicTokenGenerator {
    private static final long KSUID_EPOCH_SECONDS = 1_400_000_000L;
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CORE_LENGTH = 27;
    private static final int CHECKSUM_LENGTH = 3;
    private static final int RAW_LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 静态公共能力不需要实例或 Spring 容器。 */
    private PublicTokenGenerator() {}

    /**
     * 使用当前系统 UTC 秒及新的 128 位安全随机数生成 30 位 Base62 主体。
     *
     * @param mixingValues 可省略的有序混入值；最多 16 项，每项非 null 且 String.length() 不超过 1024
     * @return 27 位 KSUID Core 与 3 位 CRC16 校验码；业务前缀由调用方拼接
     */
    public static String generate(String... mixingValues) {
        if (mixingValues == null || mixingValues.length > 16) {
            throw new InvalidParameterException("Token 混入参数不能为 null，且最多 16 项");
        }
        byte[] mask = mixingValues.length == 0 ? null : mixingMask(mixingValues);
        long seconds = Instant.now().getEpochSecond() - KSUID_EPOCH_SECONDS;
        if (seconds < 0 || seconds > 0xffff_ffffL) {
            throw new ErrorException("当前系统时间不在 Token 可表示范围内");
        }
        byte[] payload = new byte[16];
        RANDOM.nextBytes(payload);
        if (mask != null) {
            for (int index = 0; index < payload.length; index++) {
                payload[index] ^= mask[index];
            }
        }
        byte[] raw = ByteBuffer.allocate(RAW_LENGTH).putInt((int)seconds).put(payload).array();
        return encode(new BigInteger(1, raw), CORE_LENGTH)
            + encode(BigInteger.valueOf(crc16(raw)), CHECKSUM_LENGTH);
    }

    /** 只校验无前缀主体的长度、Base62 编码与 CRC；不读取混入值、业务归属或到期时间。 */
    public static boolean isValid(String value) {
        if (value == null || value.length() != CORE_LENGTH + CHECKSUM_LENGTH) {
            return false;
        }
        String core = value.substring(0, CORE_LENGTH);
        try {
            byte[] raw = decode(core);
            return core.equals(encode(new BigInteger(1, raw), CORE_LENGTH))
                && value.substring(CORE_LENGTH).equals(encode(BigInteger.valueOf(crc16(raw)), CHECKSUM_LENGTH));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 用参数个数与各项 UTF-8 字节长度划定边界，避免直接拼接带来的歧义。 */
    private static byte[] mixingMask(String[] values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(values.length).array());
            for (String value : values) {
                if (value == null || value.length() > 1024) {
                    throw new InvalidParameterException("Token 混入值不能为 null 或超过 1024 个字符");
                }
                ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.remaining()).array());
                digest.update(encoded);
            }
            return digest.digest();
        } catch (CharacterCodingException exception) {
            throw new InvalidParameterException("Token 混入值必须是合法 Unicode 文本", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new ErrorException("JVM 不支持 SHA-256", exception);
        }
    }

    /** CRC-16/CCITT-FALSE 仅用于发现传输错误，不是签名或防伪校验。 */
    private static int crc16(byte[] bytes) {
        int crc = 0xffff;
        for (byte value : bytes) {
            crc ^= Byte.toUnsignedInt(value) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) == 0 ? (crc << 1) & 0xffff : ((crc << 1) ^ 0x1021) & 0xffff;
            }
        }
        return crc;
    }

    /** 无符号整数编码为固定长度 Base62，左侧补零。 */
    private static String encode(BigInteger value, int length) {
        StringBuilder encoded = new StringBuilder();
        BigInteger radix = BigInteger.valueOf(BASE62.length());
        do {
            BigInteger[] division = value.divideAndRemainder(radix);
            encoded.append(BASE62.charAt(division[1].intValueExact()));
            value = division[0];
        } while (value.signum() > 0);
        if (encoded.length() > length) {
            throw new IllegalArgumentException("Base62 值超出固定长度");
        }
        return "0".repeat(length - encoded.length()) + encoded.reverse();
    }

    /** 将 KSUID Core 解码为准确的 20 字节，不接受越界值或其他字符集。 */
    private static byte[] decode(String encoded) {
        BigInteger value = BigInteger.ZERO;
        BigInteger radix = BigInteger.valueOf(BASE62.length());
        for (int index = 0; index < encoded.length(); index++) {
            int digit = BASE62.indexOf(encoded.charAt(index));
            if (digit < 0) {
                throw new IllegalArgumentException("非 Base62 字符");
            }
            value = value.multiply(radix).add(BigInteger.valueOf(digit));
        }
        byte[] unpadded = value.toByteArray();
        int offset = unpadded.length > 1 && unpadded[0] == 0 ? 1 : 0;
        if (unpadded.length - offset > RAW_LENGTH) {
            throw new IllegalArgumentException("Base62 值超出原始字节长度");
        }
        byte[] result = new byte[RAW_LENGTH];
        System.arraycopy(unpadded, offset, result, RAW_LENGTH - (unpadded.length - offset), unpadded.length - offset);
        return result;
    }
}
