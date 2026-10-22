package com.paytech.platform.support.filedocument.service.processor;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.filedocument.service.processor.request.ProtectStorageCredentialRequest;
import com.paytech.platform.support.filedocument.service.processor.response.ProtectStorageCredentialResponse;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模块公开入口：把存储实例的静态访问密钥存入 Vault，返回新 Token
 * <li>调用方须在数据库事务外调用，并已完成鉴权与实例版本检查；本入口不读写数据库。</li>
 * <li>值为 FILE_STORAGE_CREDENTIAL_V1：{"accessKeyId","secretAccessKey"}，用途 FILE_STORAGE_SETUP，REFERENCE Token。</li>
 * <li>Vault 不可用、未授权或结果未知时统一返回 503；新 Token 未被实例引用前不生效。</li>
 */
@Component
@RequiredArgsConstructor
public class ProtectStorageCredentialProcessor
    extends AbstractProcessor<ProtectStorageCredentialRequest, ProtectStorageCredentialResponse> {

    /** 存储密钥的 Vault 数据类型。 */
    public static final String DATA_TYPE = "FILE_STORAGE_CREDENTIAL_V1";

    /** Platform 写入存储密钥的 Vault 用途。 */
    public static final String PURPOSE = "FILE_STORAGE_SETUP";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObjectProvider<VaultClient> vault;

    @Override
    protected ProtectStorageCredentialResponse processRequest(ProtectStorageCredentialRequest request) {
        VaultClient client = vault.getIfAvailable();
        if (client == null) {
            throw new ServiceUnavailableException();
        }
        var command = new ProtectCommand(DATA_TYPE, TokenMode.REFERENCE, credentialJson(request), PURPOSE,
            "file-storage-credential." + request.getInstanceId() + "." + UUID.randomUUID());
        try {
            return new ProtectStorageCredentialResponse(client.protect(command).token());
        } catch (FailException | ErrorException failure) {
            // 拒绝原因（含未授权）对运营不可操作，统一为依赖不可用；原因只保留在 Cause 中，不含密钥。
            throw new ServiceUnavailableException(failure);
        }
    }

    /** 固定字段顺序的密钥 JSON；只在内存中存在。 */
    static String credentialJson(ProtectStorageCredentialRequest request) {
        var value = new LinkedHashMap<String, String>();
        value.put("accessKeyId", request.getAccessKeyId());
        value.put("secretAccessKey", request.getSecretAccessKey());
        return JSON.writeValueAsString(value);
    }
}
