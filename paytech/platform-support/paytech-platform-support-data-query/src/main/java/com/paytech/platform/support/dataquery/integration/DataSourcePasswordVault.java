package com.paytech.platform.support.dataquery.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.RequiredArgsConstructor;

/**
 * 数据源密码的 Vault 保存与读取
 * <li>数据类型 DATA_SOURCE_PASSWORD_V1，REFERENCE Token；保存用途 DATA_SOURCE_CONFIGURATION，建立连接时用途 DATA_SOURCE_CONNECTION。</li>
 * <li>只在数据库事务外调用；原值不缓存、不记录、不进入异常消息。</li>
 */
@Component
@RequiredArgsConstructor
public class DataSourcePasswordVault {

    /** Vault 数据类型。 */
    public static final String DATA_TYPE = "DATA_SOURCE_PASSWORD_V1";

    /** 新建、修改数据源时保存密码的用途。 */
    public static final String CONFIGURATION_PURPOSE = "DATA_SOURCE_CONFIGURATION";

    /** 建立连接（连接池与连接检查）时读取密码的用途。 */
    public static final String CONNECTION_PURPOSE = "DATA_SOURCE_CONNECTION";

    private final VaultClient client;

    /**
     * 保存密码，返回引用 Token。
     * <li>幂等键 data-source.{id}.{sha256(密码)}：同一数据源同一密码的重试复用原记录，改密码得到新记录。</li>
     *
     * @param dataSourceId 数据源 ID（新建时为预先分配的 ID）
     * @param password 密码原文
     * @return Vault Token
     */
    public String protect(long dataSourceId, String password) {
        String key = "data-source." + dataSourceId + "." + sha256(password);
        return client.protect(new ProtectCommand(DATA_TYPE, TokenMode.REFERENCE, password, CONFIGURATION_PURPOSE, key))
            .token();
    }

    /**
     * 读取密码。
     *
     * @param token Vault Token
     * @return 密码原文
     */
    public String reveal(String token) {
        return client.reveal(new RevealQuery(token, CONNECTION_PURPOSE)).value();
    }

    /**
     * 小写十六进制 SHA-256。
     *
     * @param value 原文
     * @return 摘要
     */
    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new ErrorException("SHA-256 不可用", failure);
        }
    }
}
