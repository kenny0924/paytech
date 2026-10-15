package com.paytech.payment.card.core.configuration;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * 启用合成卡 Mock
     * <li>默认关闭；仅用于本地合成卡流程</li>
     */
    private boolean mockEnabled;

    /**
     * 合成卡 Mock 接受的测试卡号
     * <li>仅在 mockEnabled 时生效；只能配置渠道公布的测试卡号，不得配置真实卡。</li>
     * <li>默认只有 4111111111111111；本机联调可加入渠道 UAT 测试卡。</li>
     */
    private List<String> mockPans = new ArrayList<>(List.of("4111111111111111"));

    /**
     * 合成卡指纹的 HMAC-SHA256 密钥
     * <li>mockEnabled 时必填，缺失则启动失败；只用于合成卡，不是真实 Card 的指纹密钥。</li>
     * <li>同一环境保持不变，修改后同一测试卡的指纹随之变化；只配置合成测试值，不得使用真实密钥。</li>
     */
    @ToString.Exclude
    private String mockFingerprintKey;

    /** 启用合成卡时必须配置指纹密钥，不以默认值掩盖缺失。 */
    @Override
    public void afterPropertiesSet() {
        if (mockEnabled && (mockFingerprintKey == null || mockFingerprintKey.isBlank())) {
            throw new IllegalArgumentException("payment.card.mock-fingerprint-key 未配置");
        }
    }
}
