package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 标准 API 或原子 API 的一个字段定义；方向由所在数组决定。 */
public class ApiFieldBody {

    /**
     * 字段位置
     * <li>允许值：HEADER、BODY、QUERY、PATH；响应字段只允许 HEADER、BODY。</li>
     */
    @NotBlank
    @Pattern(regexp = "HEADER|BODY|QUERY|PATH", message = "字段位置只允许 HEADER、BODY、QUERY、PATH")
    private final String fieldLocation;

    /**
     * 字段路径
     * <li>BODY 为点分完整路径，数组段以 [] 结尾；最多 16 段、每段 1～64 字符，总长不超过 512。</li>
     */
    @NotBlank
    @Size(max = 512)
    private final String fieldPath;

    /**
     * 显示名称
     * <li>最多 128 字符。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String fieldName;

    /**
     * 数据类型
     * <li>允许值：STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY。</li>
     */
    @NotBlank
    @Pattern(regexp = "STRING|BOOLEAN|INTEGER|DECIMAL|OBJECT|ARRAY", message = "数据类型不受支持")
    private final String dataType;

    /**
     * 是否必填
     * <li>响应字段的必填只在 SUCCESS 分支校验。</li>
     */
    @NotNull
    private final Boolean required;

    /**
     * 字段说明
     * <li>可为 null，最多 512 字符。</li>
     */
    @Size(max = 512)
    private final String description;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiFieldBody(
        @JsonProperty("fieldLocation") String fieldLocation,
        @JsonProperty("fieldPath") String fieldPath,
        @JsonProperty("fieldName") String fieldName,
        @JsonProperty("dataType") String dataType,
        @JsonProperty("required") Boolean required,
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
    public Boolean getRequired() {
        return required;
    }

    /** 字段说明 */
    public String getDescription() {
        return description;
    }
}
