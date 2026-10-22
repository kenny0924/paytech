package com.paytech.channel.connector.protocol.domainservice.function;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.protocol.domain.execution.ExecutionErrorException;
import com.paytech.channel.connector.protocol.domain.execution.ProtectedHandle;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CHANNEL_ACCOUNT_CONFIG（账号材料交接第 3、4 节，方案 C）：以 Caller channel-connector-service（Vault Client 配置的本服务身份）、
 * Purpose CHANNEL_EXECUTION 向 Vault 读取 CHANNEL_ACCOUNT_CONFIG_V1，核对渠道、账号与经营主体与传入身份一致。
 * 服务渠道账号不绑定经营主体：负载中 entityNo 为 null，此时传入身份也不得带 entityNo（schemaVersion 仍为 1）。
 *
 * <p>parameters 为字符串映射；credentials 的值包装为受控句柄。账号状态与用途合法性由 Payment 判断，
 * 本函数只拒绝错绑的 Token。不缓存读取结果；任何失败只报固定文字，不带 Vault 原文或 Jackson 定位。
 */
@Component
@AllArgsConstructor
public class ChannelAccountConfigFunction implements InitializerFunction {

    /** 注册函数编码。 */
    public static final String CODE = "CHANNEL_ACCOUNT_CONFIG";

    /** Vault 读取用途。 */
    public static final String PURPOSE = "CHANNEL_EXECUTION";

    /** 返回属性：普通参数。 */
    public static final String PARAMETERS = "parameters";

    /** 返回属性：凭据句柄。 */
    public static final String CREDENTIALS = "credentials";

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern ENTRY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_VALUE = 8192;
    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .build();

    private final VaultClient vaultClient;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> apply(Map<String, Object> args) {
        String token = (String) args.get("vaultToken");
        if (!TOKEN.matcher(token).matches()) {
            throw new ExecutionErrorException("渠道账号 Vault 引用格式无效");
        }
        JsonNode config = decode(vaultClient.reveal(new RevealQuery(token, PURPOSE)).value());
        if (!Objects.equals(config.path("schemaVersion").asInt(0), 1) || !config.path("schemaVersion").isIntegralNumber()
            || !text(config, "channelCode").equals(args.get("channelCode"))
            || !text(config, "channelAccountId").equals(args.get("channelAccountId"))
            || !Objects.equals(nullableText(config, "entityNo"), args.get("entityNo"))) {
            throw new ExecutionErrorException("Vault 账号配置版本或归属与传入身份不一致");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put(PARAMETERS, Collections.unmodifiableMap(entries(config.path(PARAMETERS), false)));
        result.put(CREDENTIALS, Collections.unmodifiableMap(entries(config.path(CREDENTIALS), true)));
        return result;
    }

    /** 严格解析：重复属性与非对象根拒绝，错误不带原文。 */
    JsonNode decode(String value) {
        try {
            JsonNode tree = JSON.readTree(value);
            if (!tree.isObject()) {
                throw new ExecutionErrorException("Vault 账号配置结构无效");
            }
            return tree;
        } catch (JacksonException failure) {
            throw new ExecutionErrorException("Vault 账号配置不是合法的 CHANNEL_ACCOUNT_CONFIG_V1 对象");
        }
    }

    /** 可空字符串属性：服务渠道账号的 entityNo 为 null；存在时必须是字符串。 */
    static String nullableText(JsonNode config, String name) {
        if (config.path(name).isNull()) {
            return null;
        }
        return text(config, name);
    }

    /** 必填字符串属性。 */
    static String text(JsonNode config, String name) {
        if (!config.path(name).isString()) {
            throw new ExecutionErrorException("Vault 账号配置缺少字符串属性：" + name);
        }
        return config.path(name).asString();
    }

    /** 字符串键值对象，最多 64 项，名称与值有界；凭据值包装为受控句柄。 */
    static Map<String, Object> entries(JsonNode node, boolean protect) {
        if (!node.isObject() || node.size() > MAX_ENTRIES) {
            throw new ExecutionErrorException("账号参数和凭据必须是不超过 64 项的字符串键值对象");
        }
        var result = new LinkedHashMap<String, Object>();
        for (var entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (!ENTRY_NAME.matcher(entry.getKey()).matches() || !value.isString() || value.asString().isBlank()
                || value.asString().length() > MAX_VALUE) {
                throw new ExecutionErrorException("账号参数名称或值不符合约束");
            }
            result.put(entry.getKey(), protect ? new ProtectedHandle(value.asString()) : value.asString());
        }
        return result;
    }
}
