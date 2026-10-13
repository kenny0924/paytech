package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 公共脚本的一个参数声明。 */
public class ScriptParameterBody {

    /**
     * 参数名
     * <li>[a-z][A-Za-z0-9]{0,63}，脚本内唯一。</li>
     */
    @NotBlank
    @Size(max = 64)
    private final String name;

    /**
     * 数据类型
     * <li>字段类型之一或 PROTECTED_HANDLE。</li>
     */
    @NotBlank
    @Size(max = 32)
    private final String dataType;

    /** 是否必填 */
    @NotNull
    private final Boolean required;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ScriptParameterBody(
        @JsonProperty("name") String name,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("required") Boolean required) {
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
    public Boolean getRequired() {
        return required;
    }
}
