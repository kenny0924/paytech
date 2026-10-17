package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 接入方在本环境的一个出站地址。 */
public class IntegrationEndpointBody {

    /**
     * 地址代码
     * <li>接入方内唯一，原子 API 的 transport.endpointCode 引用它。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String endpointCode;

    /**
     * 基础地址
     * <li>只允许 https，必须有主机名，可带路径前缀，不允许用户信息、查询串和片段，最长 512 字符。</li>
     */
    @NotBlank
    @Size(max = 512)
    private final String baseUrl;

    /**
     * 是否沙箱地址
     * <li>可选，缺省为 false；只有沙箱地址允许沙箱验证运行。未填写时不写出，保持既有报文形状。</li>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Boolean sandbox;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationEndpointBody(
        @JsonProperty("endpointCode") String endpointCode,
        @JsonProperty("baseUrl") String baseUrl,
        @JsonProperty("sandbox") Boolean sandbox) {
        this.endpointCode = endpointCode;
        this.baseUrl = baseUrl;
        this.sandbox = sandbox;
    }

    /** 非沙箱地址。 */
    public IntegrationEndpointBody(String endpointCode, String baseUrl) {
        this(endpointCode, baseUrl, null);
    }

    /** 地址代码 */
    public String getEndpointCode() {
        return endpointCode;
    }

    /** 基础地址 */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** 是否沙箱地址 */
    public Boolean getSandbox() {
        return sandbox;
    }
}
