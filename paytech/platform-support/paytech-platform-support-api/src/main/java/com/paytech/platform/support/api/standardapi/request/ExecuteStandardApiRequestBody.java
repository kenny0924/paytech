package com.paytech.platform.support.api.standardapi.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import tools.jackson.databind.JsonNode;

/** 执行标准 API 的请求。 */
public class ExecuteStandardApiRequestBody {

    /**
     * 接入方编码
     * <li>由调用方显式选择，Platform 不从 Payment Channel 推导。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String integrationCode;

    /**
     * 标准 API 输入
     * <li>按标准 API 请求字段校验，未声明的字段拒绝；金额与精确小数用十进制字符串。</li>
     * <li>不得包含原始卡数据、密钥或凭据明文。</li>
     */
    @NotNull
    private final JsonNode input;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ExecuteStandardApiRequestBody(@JsonProperty("integrationCode") String integrationCode,
        @JsonProperty("input") JsonNode input) {
        this.integrationCode = integrationCode;
        this.input = input;
    }

    /** 接入方编码 */
    public String getIntegrationCode() {
        return integrationCode;
    }

    /** 标准 API 输入 */
    public JsonNode getInput() {
        return input;
    }
}
