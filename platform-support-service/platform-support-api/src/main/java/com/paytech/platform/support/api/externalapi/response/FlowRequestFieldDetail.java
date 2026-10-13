package com.paytech.platform.support.api.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 原子 API 请求字段及其是否已由固定值提供。 */
public class FlowRequestFieldDetail {

    /** 字段位置 */
    private final String fieldLocation;

    /** 字段路径 */
    private final String fieldPath;

    /** 显示名称 */
    private final String fieldName;

    /** 数据类型 */
    private final String dataType;

    /** 是否必填 */
    private final boolean required;

    /**
     * 说明
     * <li>可为 null。</li>
     */
    private final String description;

    /**
     * 是否已有固定值
     * <li>为 true 时节点 request 不能再赋值。</li>
     */
    private final boolean fixed;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public FlowRequestFieldDetail(
        @JsonProperty("fieldLocation") String fieldLocation,
        @JsonProperty("fieldPath") String fieldPath,
        @JsonProperty("fieldName") String fieldName,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("required") boolean required,
        @JsonProperty("description") String description,
        @JsonProperty("fixed") boolean fixed) {
        this.fieldLocation = fieldLocation;
        this.fieldPath = fieldPath;
        this.fieldName = fieldName;
        this.dataType = dataType;
        this.required = required;
        this.description = description;
        this.fixed = fixed;
    }

    /** 字段位置 */
    public String getFieldLocation() {
        return fieldLocation;
    }

    /** 字段路径 */
    public String getFieldPath() {
        return fieldPath;
    }

    /** 显示名称 */
    public String getFieldName() {
        return fieldName;
    }

    /** 数据类型 */
    public String getDataType() {
        return dataType;
    }

    /** 是否必填 */
    public boolean isRequired() {
        return required;
    }

    /** 说明 */
    public String getDescription() {
        return description;
    }

    /** 是否已有固定值 */
    public boolean isFixed() {
        return fixed;
    }
}
