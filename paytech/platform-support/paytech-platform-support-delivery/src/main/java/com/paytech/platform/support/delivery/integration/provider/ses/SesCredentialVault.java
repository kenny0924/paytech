package com.paytech.platform.support.delivery.integration.provider.ses;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.VaultClient;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 从 Vault 读取 SES 的 IAM 用户密钥对。
 * <li>值为 NOTIFICATION_PROVIDER_CREDENTIAL_V1：{"accessKeyId","secretAccessKey"}，用途 NOTIFICATION_DELIVERY</li>
 * <li>每次发送前读取，不缓存（VaultClient 合同禁止缓存原值）；原值、Token 与解析错误原文不进入异常或日志</li>
 */
@RequiredArgsConstructor
public class SesCredentialVault {

    /** Platform 读取发送账号凭据的 Vault 用途。 */
    public static final String PURPOSE = "NOTIFICATION_DELIVERY";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final VaultClient client;

    /**
     * 读取并解析账号的密钥对，返回只用于本次请求的凭据
     * <li>Vault 失败原样上抛，值格式不符时失败关闭</li>
     *
     * @param token 账号的密钥 Token
     */
    public AwsCredentialsProvider reveal(String token) {
        String value = client.reveal(new RevealQuery(token, PURPOSE)).value();
        JsonNode node;
        try {
            node = JSON.readTree(value);
        } catch (JacksonException malformed) {
            // 解析异常可能回显密钥片段，不保留原因链。
            throw new ErrorException("SES 凭据格式不合法");
        }
        String accessKeyId = text(node, "accessKeyId");
        String secretAccessKey = text(node, "secretAccessKey");
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    /** 读取必填的非空字符串字段。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new ErrorException("SES 凭据缺少 " + field);
        }
        return value.asString();
    }
}
