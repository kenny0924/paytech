package com.paytech.platform.support.filedocument.integration.storage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 从 Vault 读取存储实例的静态访问密钥
 * <li>值为 FILE_STORAGE_CREDENTIAL_V1：{"accessKeyId","secretAccessKey"}，用途 FILE_STORAGE_ACCESS。</li>
 * <li>每次存储操作前读取，不缓存（VaultClient 合同禁止缓存原值）；原值、Token 与解析错误原文不进入异常或日志。</li>
 * <li>Vault 不可用、拒绝或值格式不符统一为依赖不可用（503），调用方不发起存储调用。</li>
 */
@Component
@AllArgsConstructor
public class StorageCredentialVault {

    /** Platform 读取存储密钥的 Vault 用途。 */
    public static final String PURPOSE = "FILE_STORAGE_ACCESS";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObjectProvider<VaultClient> vault;

    /**
     * 读取并解析实例密钥，只用于本次操作
     *
     * @param token 实例保存的 Vault Token
     * @return 本次使用的密钥
     */
    public StorageCredential reveal(String token) {
        VaultClient client = vault.getIfAvailable();
        if (client == null) {
            throw new ServiceUnavailableException();
        }
        String value;
        try {
            value = client.reveal(new RevealQuery(token, PURPOSE)).value();
        } catch (FailException | ErrorException failure) {
            throw new ServiceUnavailableException(failure);
        }
        return parse(value);
    }

    /** 解析密钥 JSON；解析异常可能回显密钥片段，不保留原因链。 */
    static StorageCredential parse(String value) {
        JsonNode node;
        try {
            node = JSON.readTree(value);
        } catch (JacksonException malformed) {
            throw new ServiceUnavailableException(new ErrorException("存储密钥格式不合法"));
        }
        return new StorageCredential(text(node, "accessKeyId"), text(node, "secretAccessKey"));
    }

    /** 读取必填的非空字符串字段。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new ServiceUnavailableException(new ErrorException("存储密钥缺少 " + field));
        }
        return value.asString();
    }
}
