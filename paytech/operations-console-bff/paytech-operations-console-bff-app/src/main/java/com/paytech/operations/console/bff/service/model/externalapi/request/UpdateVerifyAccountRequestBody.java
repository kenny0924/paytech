package com.paytech.operations.console.bff.service.model.externalapi.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 按当前版本整体修改沙箱验证账号。 */
public class UpdateVerifyAccountRequestBody {

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
    public UpdateVerifyAccountRequestBody(
        @JsonProperty("accountName") String accountName,
        @JsonProperty("channelCode") String channelCode,
        @JsonProperty("channelAccountId") String channelAccountId,
        @JsonProperty("entityNo") String entityNo,
        @JsonProperty("parameters") Map<String, String> parameters,
        @JsonProperty("credentials") Map<String, String> credentials,
        @JsonProperty("expectedVersion") Integer expectedVersion,
        @JsonProperty("reason") String reason) {
        this.accountName = accountName;
        this.channelCode = channelCode;
        this.channelAccountId = channelAccountId;
        this.entityNo = entityNo;
        this.parameters = parameters == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters));
        this.credentials = credentials == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(credentials));
        this.expectedVersion = expectedVersion;
        this.reason = reason;
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

    /** 期望版本 */
    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    /** 原因 */
    public String getReason() {
        return reason;
    }
}
