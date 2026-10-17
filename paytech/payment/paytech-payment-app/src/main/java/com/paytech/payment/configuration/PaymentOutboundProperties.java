package com.paytech.payment.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Execution 出站依赖（channel-connector、Risk 统一评估）的私网地址与超时；修改需重启。 */
@Data
@Validated
@ConfigurationProperties(prefix = "payment.outbound")
public class PaymentOutboundProperties {
    /**
     * channel-connector 的应用 context path
     * <li>请求地址为 http://channel-connector-service{本值}/channel-connector/v1/...；channel-connector 无 context path，默认空串。</li>
     */
    @NotNull
    @Pattern(regexp = "|/[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*")
    private String channelConnectorContextPath = "";

    /** MID 候选查询的读取超时；只读配置查询，默认 2 秒。 */
    @NotNull
    private Duration channelCandidateReadTimeout = Duration.ofSeconds(2);

    /**
     * 渠道操作的读取超时
     * <li>须大于 channel-connector 调用 Platform 的读取超时（默认 30 秒）；超时按结果未知处理，不重试。</li>
     */
    @NotNull
    private Duration channelOperationReadTimeout = Duration.ofSeconds(35);

    /**
     * Risk 服务的应用 context path
     * <li>请求地址为 http://risk-service{本值}/risk-decisions/v1/evaluations；Risk 无 context path，默认空串。</li>
     */
    @NotNull
    @Pattern(regexp = "|/[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*")
    private String riskContextPath = "";

    /**
     * Risk 统一评估的读取超时
     * <li>默认 1 秒（Risk 计算超时为 500 毫秒）；超时后用同一请求重试一次，仍超时则失败关闭，最坏约两倍本值。</li>
     */
    @NotNull
    private Duration riskReadTimeout = Duration.ofSeconds(1);
}
