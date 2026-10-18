package com.paytech.operations.console.bff.service.model.externalapi.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 在接入方下创建沙箱验证账号。 */
public class CreateVerifyAccountRequestBody {

    /**
     * 账号编码
     * <li>接入方内唯一，创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String accountCode;

    /**
     * 名称
     * <li>去除首尾空白后 1～128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String accountName;

    /**
     * 渠道编码
     * <li>流程初始化时与输入中的账号身份核对。</li>
     */
    @NotBlank
    @Size(max = 64)
    private final String channelCode;

    /** 渠道账号标识 */
    @NotBlank
    @Size(max = 64)
    private final String channelAccountId;

    /** 经营主体编号 */
    @NotBlank
    @Size(max = 64)
    private final String entityNo;

    /**
     * 普通参数
     * <li>名称以字母开头，只含字母、数字、_ . -，最多 64 字符；值 1～8192 字符；最多 64 项。</li>
     */
    @NotNull
    @Size(max = 64)
    private final Map<String, String> parameters;

    /**
     * 沙箱凭据
     * <li>按用户决定明文保存的非敏感沙箱材料；名称以字母开头，只含字母、数字、_ . -，最多 64 字符；值 1～8192 字符；最多 64 项。</li>
     */
    @NotNull
    @Size(max = 64)
    private final Map<String, String> credentials;

    /** 原因 */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreateVerifyAccountRequestBody(
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("channelCode") String channelCode,
        @JsonProperty("channelAccountId") String channelAccountId,
        @JsonProperty("entityNo") String entityNo,
        @JsonProperty("parameters") Map<String, String> parameters,
        @JsonProperty("credentials") Map<String, String> credentials,
        @JsonProperty("reason") String reason) {
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.channelCode = channelCode;
        this.channelAccountId = channelAccountId;
        this.entityNo = entityNo;
        this.parameters = parameters == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters));
        this.credentials = credentials == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(credentials));
        this.reason = reason;
    }

    /** 账号编码 */
    public String getAccountCode() {
        return accountCode;
    }

    /** 名称 */
    public String getAccountName() {
        return accountName;
    }

    /** 渠道编码 */
    public String getChannelCode() {
        return channelCode;
    }

    /** 渠道账号标识 */
    public String getChannelAccountId() {
        return channelAccountId;
    }

    /** 经营主体编号 */
    public String getEntityNo() {
        return entityNo;
    }

    /** 普通参数 */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /** 沙箱凭据 */
    public Map<String, String> getCredentials() {
        return credentials;
    }

    /** 原因 */
    public String getReason() {
        return reason;
    }
}
