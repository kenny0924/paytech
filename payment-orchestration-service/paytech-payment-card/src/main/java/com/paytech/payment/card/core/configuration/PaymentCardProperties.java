package com.paytech.payment.card.core.configuration;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 卡工具配置 */
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "payment.card")
public class PaymentCardProperties {
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
}
