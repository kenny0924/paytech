package com.paytech.channel.connector.api.externalapi.response;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 渠道标准 API 实现详情。 */
public class ImplementationResponseBody {

    /** 标准 API 编码 */
    private final String standardApiCode;

    /** 流程定义 */
    private final JsonNode flowDefinition;

    /** 当前版本 */
    private final int version;

    /**
     * 创建时间
     * <li>UTC。</li>
     */
    private final Instant createTime;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /**
     * 最后修改人
     * <li>Operations 账号标识，十进制字符串。</li>
     */
    private final String updatedBy;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ImplementationResponseBody(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("flowDefinition") JsonNode flowDefinition,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime,
        @JsonProperty("updatedBy") String updatedBy) {
        this.standardApiCode = standardApiCode;
        this.flowDefinition = flowDefinition;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.updatedBy = updatedBy;
    }

    /** 标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 流程定义 */
    public JsonNode getFlowDefinition() {
        return flowDefinition;
    }

    /** 当前版本 */
    public int getVersion() {
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

    /** 最后修改人 */
    public String getUpdatedBy() {
        return updatedBy;
    }
}
