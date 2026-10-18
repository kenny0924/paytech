package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本整体修改沙箱验证用例。 */
public class UpdateVerifyCaseRequestBody {

    /**
     * 名称
     * <li>去除首尾空白后 1～128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String caseName;

    /** 说明 */
    @Size(max = 512)
    private final String description;

    /** 验证账号编码 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String accountCode;

    /**
     * 步骤
     * <li>1～10 步，每步 {id, standardApiCode, input, expect}，结构见沙箱验证技术方案第 4 节。</li>
     */
    @NotNull
    private final JsonNode steps;

    /**
     * 期望版本
     * <li>与当前版本不一致返回 409。</li>
     */
    @NotNull
    @Positive
    private final Integer expectedVersion;

    /** 原因 */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public UpdateVerifyCaseRequestBody(
        @JsonProperty("caseName") String caseName,
        @JsonProperty("description") String description,
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("steps") JsonNode steps,
        @JsonProperty("expectedVersion") Integer expectedVersion,
        @JsonProperty("reason") String reason) {
        this.caseName = caseName;
        this.description = description;
        this.accountCode = accountCode;
        this.steps = steps;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 名称 */
    public String getCaseName() {
        return caseName;
    }

    /** 说明 */
    public String getDescription() {
        return description;
    }

    /** 验证账号编码 */
    public String getAccountCode() {
        return accountCode;
    }

    /** 步骤 */
    public JsonNode getSteps() {
        return steps;
    }

    /** 期望版本 */
    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    /** 原因 */
    public String getReason() {
        return reason;
    }
}
