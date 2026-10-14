package com.paytech.platform.support.api.externalapi.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证账号详情。 */
public class VerifyAccountResponseBody {

    /** 账号编码 */
    private final String accountCode;

    /** 名称 */
    private final String accountName;

    /** 渠道编码 */
    private final String channelCode;

    /** 渠道账号标识 */
    private final String channelAccountId;

    /** 经营主体编号 */
    private final String entityNo;

    /** 普通参数 */
    private final Map<String, String> parameters;

    /**
     * 沙箱凭据
     * <li>非敏感沙箱材料，原样返回。</li>
     */
    private final Map<String, String> credentials;

    /**
     * 引用该账号的用例编码
     * <li>非空时不能删除。</li>
     */
    private final List<String> referencedCaseCodes;

    /** 版本 */
    private final Integer version;

    /** 创建时间 */
    private final Instant createTime;

    /** 最后修改时间 */
    private final Instant updateTime;

    /** 最后修改账号 */
    private final String updatedBy;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyAccountResponseBody(
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("channelCode") String channelCode,
        @JsonProperty("channelAccountId") String channelAccountId,
        @JsonProperty("entityNo") String entityNo,
        @JsonProperty("parameters") Map<String, String> parameters,
        @JsonProperty("credentials") Map<String, String> credentials,
        @JsonProperty("referencedCaseCodes") List<String> referencedCaseCodes,
        @JsonProperty("version") Integer version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.channelCode = channelCode;
        this.channelAccountId = channelAccountId;
        this.entityNo = entityNo;
        this.parameters = parameters == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters));
        this.credentials = credentials == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(credentials));
        this.referencedCaseCodes = referencedCaseCodes == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(referencedCaseCodes));
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
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

    /** 引用该账号的用例编码 */
    public List<String> getReferencedCaseCodes() {
        return referencedCaseCodes;
    }

    /** 版本 */
    public Integer getVersion() {
        return version;
    }

    /** 创建时间 */
    public Instant getCreateTime() {
        return createTime;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }

    /** 最后修改账号 */
    public String getUpdatedBy() {
        return updatedBy;
    }
}
