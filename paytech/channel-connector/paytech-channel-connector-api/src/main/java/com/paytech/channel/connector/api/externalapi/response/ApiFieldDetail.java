package com.paytech.channel.connector.api.externalapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 已保存的字段定义，顺序即显示顺序。 */
public class ApiFieldDetail {

    /**
     * 字段位置
     * <li>取值同 ApiFieldLocation。</li>
     */
    private final String fieldLocation;

    /** 字段路径 */
    private final String fieldPath;

    /** 显示名称 */
    private final String fieldName;

    /**
     * 数据类型
     * <li>取值同 ApiFieldDataType。</li>
     */
    private final String dataType;

    /** 是否必填 */
    private final boolean required;

    /**
     * 字段说明
     * <li>可为 null。</li>
     */
    private final String description;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiFieldDetail(
        @JsonProperty("fieldLocation") String fieldLocation,
        @JsonProperty("fieldPath") String fieldPath,
        @JsonProperty("fieldName") String fieldName,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("required") boolean required,
        @JsonProperty("description") String description) {
        this.fieldLocation = fieldLocation;
        this.fieldPath = fieldPath;
        this.fieldName = fieldName;
        this.dataType = dataType;
        this.required = required;
        this.description = description;
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

    /** 字段说明 */
    public String getDescription() {
        return description;
    }
}
