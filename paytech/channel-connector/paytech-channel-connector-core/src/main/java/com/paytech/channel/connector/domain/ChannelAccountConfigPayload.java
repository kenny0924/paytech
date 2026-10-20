package com.paytech.channel.connector.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * 待 Protect 的 CHANNEL_ACCOUNT_CONFIG_V1 原值与幂等键
 * <li>结构与 {@code ChannelAccountConfigReader} 读取的对象完全对称：schemaVersion=1、channelCode、channelAccountId（十进制字符串）、
 * entityNo、parameters、credentials，由本地账号与渠道记录填充归属字段。</li>
 * <li>原值为 RFC 8785 规范 JSON 的 UTF-8 文本；幂等键为 {@value #KEY_PREFIX}{channelAccountId}.{规范字节 SHA-256 hex}，
 * 同内容重放复用同一键，不同内容产生新 Token。</li>
 * <li>原值含凭据：禁止日志、异常、持久化或进入变更记录；toString 不输出内容。</li>
 */
public final class ChannelAccountConfigPayload {
    /** Vault 数据类型。 */
    public static final String DATA_TYPE = "CHANNEL_ACCOUNT_CONFIG_V1";

    /** Vault 访问用途：运营写入渠道账号配置。 */
    public static final String PURPOSE = "CHANNEL_CONFIGURATION";

    /**
     * 幂等键前缀
     * <li>Vault Client 的幂等键只允许 [A-Za-z0-9._~-]，因此分隔符用点号而不是 technique 草案中的冒号。</li>
     */
    public static final String KEY_PREFIX = "channel-account-config.";

    /** Vault 单个原值的 UTF-8 字节上限。 */
    public static final int MAX_VALUE_BYTES = 32768;

    private static final JsonFactory JSON = new JsonFactory();

    /** 规范 JSON 文本。 */
    private final String value;

    /** 逻辑命令幂等键。 */
    private final String idempotencyKey;

    /** 由已规范化的字节构造，私有以保证只经工厂生成。 */
    private ChannelAccountConfigPayload(String value, String idempotencyKey) {
        this.value = value;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * 构造规范原值与幂等键
     *
     * @param channel 账号所属渠道
     * @param account 被配置的账号
     * @param parameters 已校验的账号参数
     * @param credentials 已校验的凭据
     * @return 规范原值与幂等键
     */
    public static ChannelAccountConfigPayload of(PaymentChannel channel, ChannelAccount account,
        Map<String, String> parameters, Map<String, String> credentials) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON.createGenerator(ObjectWriteContext.empty(), json)) {
            generator.writeStartObject();
            generator.writeNumberProperty("schemaVersion", 1);
            generator.writeStringProperty("channelCode", channel.getChannelCode());
            generator.writeStringProperty("channelAccountId", account.getChannelAccountId().toString());
            generator.writeStringProperty("entityNo", account.getEntityNo());
            writeEntries(generator, "parameters", parameters);
            writeEntries(generator, "credentials", credentials);
            generator.writeEndObject();
        }
        byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(json.toByteArray());
        return new ChannelAccountConfigPayload(new String(canonical, StandardCharsets.UTF_8),
            KEY_PREFIX + account.getChannelAccountId() + "." + sha256(canonical));
    }

    /** 写出一个字符串键值对象；顺序由规范化统一。 */
    static void writeEntries(JsonGenerator generator, String name, Map<String, String> entries) {
        generator.writeName(name);
        generator.writeStartObject();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            generator.writeStringProperty(entry.getKey(), entry.getValue());
        }
        generator.writeEndObject();
    }

    /** 计算 SHA-256 小写十六进制。 */
    static String sha256(byte[] canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new ErrorException("运行环境缺少 SHA-256", e);
        }
    }

    /** 规范 JSON 原值；仅交给 Vault Protect，不得记录。 */
    public String value() {
        return value;
    }

    /** 逻辑命令幂等键；不含凭据，可用于诊断。 */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** 原值 UTF-8 字节数是否超过 Vault 上限。 */
    public boolean tooLarge() {
        return value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES;
    }

    /** 不输出原值。 */
    @Override
    public String toString() {
        return "ChannelAccountConfigPayload[redacted]";
    }
}
