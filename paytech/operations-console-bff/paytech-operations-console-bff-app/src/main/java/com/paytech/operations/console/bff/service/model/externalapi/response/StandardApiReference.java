package com.paytech.operations.console.bff.service.model.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 被某接入方实现的标准 API。 */
public class StandardApiReference {

    /** 标准 API 编码 */
    private final String standardApiCode;

    /** 标准 API 名称 */
    private final String standardApiName;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StandardApiReference(
        @JsonProperty("standardApiCode") String standardApiCode,
        @JsonProperty("standardApiName") String standardApiName) {
        this.standardApiCode = standardApiCode;
        this.standardApiName = standardApiName;
    }

    /** 标准 API 编码 */
    public String getStandardApiCode() {
        return standardApiCode;
    }

    /** 标准 API 名称 */
    public String getStandardApiName() {
        return standardApiName;
    }
}
