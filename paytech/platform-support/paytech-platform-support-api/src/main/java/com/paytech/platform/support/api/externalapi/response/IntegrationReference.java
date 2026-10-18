package com.paytech.platform.support.api.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 引用某配置的接入方。 */
public class IntegrationReference {

    /** 接入方编码 */
    private final String integrationCode;

    /** 接入方名称 */
    private final String integrationName;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationReference(
        @JsonProperty("integrationCode") String integrationCode,
        @JsonProperty("integrationName") String integrationName) {
        this.integrationCode = integrationCode;
        this.integrationName = integrationName;
    }

    /** 接入方编码 */
    public String getIntegrationCode() {
        return integrationCode;
    }

    /** 接入方名称 */
    public String getIntegrationName() {
        return integrationName;
    }
}
