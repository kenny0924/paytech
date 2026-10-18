package com.paytech.platform.support.api.externalapi.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 渠道标准 API 实现列表项。 */
public class ImplementationSummary {

    /** 标准 API 编码 */
    private final String standardApiCode;

    /** 标准 API 名称 */
    private final String standardApiName;

    /** 节点数 */
    private final int nodeCount;

    /** 出口数 */
    private final int returnCount;

    /**
     * 初始化项标识
     * <li>按声明顺序。</li>
     */
    private final List<String> initializerIds;

    /**
     * 流程总预算
     * <li>毫秒。</li>
     */
    private final int timeoutMs;

    /** 当前版本 */
    private final int version;

    /**
     * 最后修改时间
     * <li>UTC。</li>
     */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ImplementationSummary(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("standardApiName") String standardApiName,
        @JsonProperty("nodeCount") int nodeCount,
        @JsonProperty("returnCount") int returnCount,
        @JsonProperty("initializerIds") List<String> initializerIds,
        @JsonProperty("timeoutMs") int timeoutMs,
        @JsonProperty("version") int version,
        @JsonProperty("updateTime") Instant updateTime) {
        this.standardApiCode = standardApiCode;
        this.standardApiName = standardApiName;
        this.nodeCount = nodeCount;
        this.returnCount = returnCount;
        this.initializerIds = initializerIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(initializerIds));
        this.timeoutMs = timeoutMs;
        this.version = version;
        this.updateTime = updateTime;
    }

    /** 标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 标准 API 名称 */
    public String getStandardApiName() {
        return standardApiName;
    }

    /** 节点数 */
    public int getNodeCount() {
        return nodeCount;
    }

    /** 出口数 */
    public int getReturnCount() {
        return returnCount;
    }

    /** 初始化项标识 */
    public List<String> getInitializerIds() {
        return initializerIds;
    }

    /** 流程总预算 */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /** 当前版本 */
    public int getVersion() {
        return version;
    }

    /** 最后修改时间 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
