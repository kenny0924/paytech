package com.paytech.operations.console.bff.service.model.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 已保存的脚本参数声明。 */
public class ScriptParameterDetail {

    /** 参数名 */
    private final String name;

    /** 数据类型 */
    private final String dataType;

    /** 是否必填 */
    private final boolean required;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ScriptParameterDetail(
        @JsonProperty("name") String name,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("required") boolean required) {
        this.name = name;
        this.dataType = dataType;
        this.required = required;
    }

    /** 参数名 */
    public String getName() {
        return name;
    }

    /** 数据类型 */
    public String getDataType() {
        return dataType;
    }

    /** 是否必填 */
    public boolean isRequired() {
        return required;
    }
}
