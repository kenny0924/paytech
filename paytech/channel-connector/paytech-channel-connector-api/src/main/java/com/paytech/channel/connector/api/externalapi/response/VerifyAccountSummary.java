package com.paytech.channel.connector.api.externalapi.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 验证账号列表项。 */
public class VerifyAccountSummary {

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

    /** 版本 */
    private final Integer version;

    /** 最后修改时间 */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public VerifyAccountSummary(
        @JsonProperty("accountCode") String accountCode,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("channelCode") String channelCode,
        @JsonProperty("channelAccountId") String channelAccountId,
        @JsonProperty("entityNo") String entityNo,
        @JsonProperty("version") Integer version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.channelCode = channelCode;
        this.channelAccountId = channelAccountId;
        this.entityNo = entityNo;
        this.version = version;
        this.updateTime = updateTime;
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

    /** 版本 */
    public Integer getVersion() {
        return version;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
