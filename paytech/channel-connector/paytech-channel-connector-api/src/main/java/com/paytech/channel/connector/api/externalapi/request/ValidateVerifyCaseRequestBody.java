package com.paytech.channel.connector.api.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 只校验不保存的沙箱验证用例。 */
public class ValidateVerifyCaseRequestBody {

    /** 验证账号编码 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String accountCode;

    /** 步骤 */
    @NotNull
    private final JsonNode steps;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ValidateVerifyCaseRequestBody(
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("steps") JsonNode steps) {
        this.accountCode = accountCode;
        this.steps = steps;
    }

    /** 验证账号编码 */
    public String getAccountCode() {
        return accountCode;
    }

    /** 步骤 */
    public JsonNode getSteps() {
        return steps;
    }
}
