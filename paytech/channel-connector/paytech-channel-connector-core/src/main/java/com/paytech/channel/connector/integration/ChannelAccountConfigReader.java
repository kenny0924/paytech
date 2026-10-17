package com.paytech.channel.connector.integration;

import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.channel.connector.domain.ChannelAccountConfiguration;
import com.paytech.channel.connector.domain.ChannelExecutionContext;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 在业务归属核对后访问真实 Vault Client，并解释 channel-connector 拥有的账号 Payload。 */
@Component
@AllArgsConstructor
public class ChannelAccountConfigReader {
    /** Vault HTTP 客户端，不在数据库事务内访问。 */
    private final VaultClient vaultClient;

    /** 严格解析账号合同，独立于普通 HTTP Body 的宿主序列化策略。 */
    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    /** 只还原本次冻结账号；缺失、拒绝或结构错误直接失败，不回退其他账号。 */
    public ChannelAccountConfiguration read(ChannelExecutionContext context) {
        String token = context.getAccount().getAccountVaultToken();
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ErrorException("渠道账号缺少有效的 Vault 配置引用");
        }
        String value = vaultClient.reveal(new RevealQuery(token, "CHANNEL_EXECUTION")).value();
        var configuration = decode(value);
        if (!Objects.equals(configuration.getSchemaVersion(), 1)
            || !Objects.equals(configuration.getChannelCode(), context.getChannel().getChannelCode())
            || !Objects.equals(configuration.getChannelAccountId(), context.getAccount().getChannelAccountId().toString())
            || !Objects.equals(configuration.getEntityNo(), context.getAccount().getEntityNo())) {
            throw new ErrorException("Vault 账号配置版本或归属不一致");
        }
        validateEntries(configuration.getParameters());
        validateEntries(configuration.getCredentials());
        return configuration;
    }

    /** 敏感解析错误不携带原 JSON 或 Jackson 字段值进入日志。 */
    ChannelAccountConfiguration decode(String value) {
        try {
            var tree = JSON.readTree(value);
            if (!tree.isObject() || !tree.path("schemaVersion").isIntegralNumber()
                || !tree.path("channelCode").isString() || !tree.path("channelAccountId").isString()
                || !tree.path("entityNo").isString()) {
                throw new ErrorException("Vault 账号配置结构无效");
            }
            validateMapShape(tree.path("parameters"));
            validateMapShape(tree.path("credentials"));
            return JSON.treeToValue(tree, ChannelAccountConfiguration.class);
        } catch (JacksonException failure) {
            // Jackson 的 Cause 可能包含密钥值，按本合同的零泄漏边界只报告固定诊断。
            throw new ErrorException("Vault 账号配置不是合法的 CHANNEL_ACCOUNT_CONFIG_V1 对象");
        }
    }

    /** 拒绝嵌套、数字和空值，避免 Jackson 把配置值隐式转换成字符串。 */
    void validateMapShape(tools.jackson.databind.JsonNode node) {
        if (!node.isObject()) {
            throw new ErrorException("账号参数和凭据必须是字符串键值对象");
        }
        for (var value : node) {
            if (!value.isString()) {
                throw new ErrorException("账号参数和凭据只能包含字符串值");
            }
        }
    }

    /** 有界参数集合由渠道配置维护方赋予含义；该通用查询不猜测 outletId 等字段。 */
    void validateEntries(Map<String, String> entries) {
        if (entries == null || entries.size() > 64) {
            throw new ErrorException("账号参数集合缺失或超过上限");
        }
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                || entry.getValue() == null || entry.getValue().isBlank() || entry.getValue().length() > 8192) {
                throw new ErrorException("账号参数名称或值不符合约束");
            }
        }
    }
}
