package com.paytech.channel.connector.api.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 外部 API 配置写入结果。 */
public class ExternalApiMutationResponseBody {

    /**
     * 对象类型
     * <li>如 STANDARD_API、INTEGRATION。</li>
     */
    private final String objectType;

    /**
     * 对象编码
     * <li>原子 API、脚本与实现为“接入方编码/对象编码”。</li>
     */
    private final String objectCode;

    /**
     * 保存后的版本
     * <li>删除返回删除前的版本。</li>
     */
    private final int version;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ExternalApiMutationResponseBody(
        @JsonProperty("objectType") String objectType,
        @JsonProperty("objectCode") String objectCode,
        @JsonProperty("version") int version) {
        this.objectType = objectType;
        this.objectCode = objectCode;
        this.version = version;
    }

    /** 对象类型 */
    public String getObjectType() {
        return objectType;
    }

    /** 对象编码 */
    public String getObjectCode() {
        return objectCode;
    }

    /** 保存后的版本 */
    public int getVersion() {
        return version;
    }
}
