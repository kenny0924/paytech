package com.paytech.channel.connector.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 出站依赖 Platform 标准 API 的私网地址与超时；修改需重启。 */
@Data
@Validated
@ConfigurationProperties(prefix = "channel-connector.outbound")
public class ChannelConnectorOutboundProperties {
    /**
     * Platform 服务的应用 context path
     * <li>请求地址为 http://platform-support-service{本值}/standard-apis/v1/...；默认 /platform-support。</li>
     * <li>只有测试 Mock HTTP 服务配置为空串。</li>
     */
    @NotNull
    @Pattern(regexp = "|/[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*")
    private String platformContextPath = "/platform-support";

    /**
     * Platform 标准 API 执行的读取超时
     * <li>须大于渠道流程总预算（PayerMax PAY 为 25 秒）；超时按结果未知处理。</li>
     */
    @NotNull
    private Duration platformReadTimeout = Duration.ofSeconds(30);
}
