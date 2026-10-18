package com.paytech.platform.support.api.configurationmanagement.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 保存当次的身份、状态及版本；重放不读取后续的新版本。 */
public final class ConfigurationMutationResponseBody {
    /** 保存对象类型。 */
    private final String objectType;

    /** 保存对象主键。 */
    private final String objectId;

    /** 保存对象编码。 */
    private final String objectCode;

    /** 当前或请求的配置状态。 */
    private final String status;

    /** 当前版本。 */
    private final int version;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ConfigurationMutationResponseBody(@JsonProperty(value = "objectType") String objectType,
        @JsonProperty(value = "objectId") String objectId,
        @JsonProperty(value = "objectCode") String objectCode,
        @JsonProperty(value = "status") String status,
        @JsonProperty(value = "version") int version) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.objectCode = objectCode;
        this.status = status;
        this.version = version;
    }

    /** 保存对象类型。 */
    public String getObjectType() {
        return objectType;
    }

    /** 保存对象主键。 */
    public String getObjectId() {
        return objectId;
    }

    /** 保存对象编码。 */
    public String getObjectCode() {
        return objectCode;
    }

    /** 当前或请求的配置状态。 */
    public String getStatus() {
        return status;
    }

    /** 当前版本。 */
    public int getVersion() {
        return version;
    }
}
