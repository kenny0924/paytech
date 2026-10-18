package com.paytech.platform.support.integration.service.processor.request;

import com.paytech.platform.support.integration.domain.execution.VerificationScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

import tools.jackson.databind.JsonNode;

/** 执行标准 API 的输入。 */
@Getter
@AllArgsConstructor
public class ExecuteStandardApiRequest {

    /** 路径中的标准 API 编码。 */
    @NotBlank
    private final String standardApiCode;

    /** 调用方选择的接入方编码。 */
    @NotBlank
    private final String integrationCode;

    /** 标准 API 输入原始 JSON。 */
    @NotNull
    private final JsonNode input;

    /** 沙箱验证附加项：只由验证运行传入；HTTP 内部执行接口为 null。 */
    private final VerificationScope verification;

    /** 正式执行。 */
    public ExecuteStandardApiRequest(String standardApiCode, String integrationCode, JsonNode input) {
        this(standardApiCode, integrationCode, input, null);
    }
}
