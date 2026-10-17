package com.paytech.card.core.configuration;

import java.util.Base64;

import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 卡工具配置 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "payment.card")
public class PaymentCardProperties implements InitializingBean {
    /** 指纹密钥解码后的最小字节数。 */
    static final int MIN_FINGERPRINT_KEY_BYTES = 32;

    /**
     * 卡指纹与卡凭证摘要的 HMAC-SHA256 密钥，Base64 编码
     * <li>必填，解码后至少 32 字节；缺失或无效时启动失败。</li>
     * <li>单一密钥、不可轮换：卡按指纹唯一、按卡号定位都依赖它，更换后已存的卡与凭证都无法再匹配。</li>
     * <li>只经环境变量注入，不写入 Nacos 或版本库。</li>
     */
    @ToString.Exclude
    private String fingerprintKey;

    /** 启动时校验指纹密钥，不以默认值掩盖缺失。 */
    @Override
    public void afterPropertiesSet() {
        fingerprintKeyBytes();
    }

    /**
     * 解码指纹密钥
     *
     * @return 密钥字节，每次返回新数组
     * @throws IllegalArgumentException 未配置、不是 Base64 或长度不足
     */
    public byte[] fingerprintKeyBytes() {
        if (fingerprintKey == null || fingerprintKey.isBlank()) {
            throw new IllegalArgumentException("payment.card.fingerprint-key 未配置");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(fingerprintKey);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("payment.card.fingerprint-key 不是有效的 Base64");
        }
        if (key.length < MIN_FINGERPRINT_KEY_BYTES) {
            throw new IllegalArgumentException("payment.card.fingerprint-key 解码后不足 32 字节");
        }
        return key;
    }
}
