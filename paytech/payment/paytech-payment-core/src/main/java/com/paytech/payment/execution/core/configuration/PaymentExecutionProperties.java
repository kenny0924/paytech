package com.paytech.payment.execution.core.configuration;

import java.time.Duration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Pattern;

/**
 * 支付执行配置
 *
 * @author lzb
 */
@Data
@Validated
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "payment.execution")
public class PaymentExecutionProperties {
    /**
     * 支付工具申请有效期
     * <li>使用 Duration，例如 PT5M；零值或负值不接受新申请</li>
     * <li>实际到期时间不超过 Intent 和 Card 返回的期限</li>
     */
    private Duration instrumentPreparationWindow = Duration.ZERO;

    /**
     * 渠道支付通知入口的公网根地址
     * <li>例如 https://api.example.com；PAY 的 notifyUrl 为 {根地址}/api/channel-callbacks/v1/{channelCode}/{channelAccountId}。</li>
     * <li>为空时不传 notifyUrl，只靠查单确认结果；非空时必须是不以斜杠结尾的 https 地址。</li>
     */
    @Pattern(regexp = "|https://[^\\s/?#]+(/[^\\s?#]*[^\\s?#/])?", message = "渠道通知根地址须为空或不以斜杠结尾的 https 地址")
    private String channelCallbackBaseUrl;

    /** 本次 PAY 的渠道通知地址；未配置根地址或缺少渠道编码时为 null。 */
    public String channelCallbackUrl(String channelCode, Long channelAccountId) {
        if (channelCallbackBaseUrl == null || channelCallbackBaseUrl.isEmpty() || channelCode == null) {
            return null;
        }
        return channelCallbackBaseUrl + "/api/channel-callbacks/v1/" + channelCode + "/" + channelAccountId;
    }
}
