package com.paytech.channel.connector.protocol.service.processor.request;

import com.paytech.channel.connector.protocol.domain.execution.VerificationScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 执行标准 API 的输入。 */
@Getter
@AllArgsConstructor
public class ExecuteStandardApiRequest {

    /** 标准 API 编码。 */
    @NotBlank
    private final String standardApiCode;

    /** 调用方选择的渠道编码。 */
    @NotBlank
    private final String channelCode;

    /** 标准 API 输入原始 JSON。 */
    @NotNull
    private final JsonNode input;

    /**
     * 调用方服务名，只写入对外调用访问日志
     * <li>支付入口为 Payment 调用方，通用入口为已确认的调用方，沙箱验证为 {@value #VERIFY_CALLER}。</li>
     */
    @NotBlank
    private final String callerService;

    /** 沙箱验证附加项：只由验证运行传入；正式执行（进程内调用）为 null。 */
    private final VerificationScope verification;

    /** 沙箱验证运行在访问日志中的调用方。 */
    public static final String VERIFY_CALLER = "VERIFY";

    /** 正式执行。 */
    public ExecuteStandardApiRequest(String standardApiCode, String channelCode, JsonNode input,
        String callerService) {
        this(standardApiCode, channelCode, input, callerService, null);
    }
}
