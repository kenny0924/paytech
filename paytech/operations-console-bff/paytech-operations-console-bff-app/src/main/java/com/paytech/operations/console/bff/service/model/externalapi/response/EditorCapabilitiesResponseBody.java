package com.paytech.operations.console.bff.service.model.externalapi.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 编辑器能力目录。 */
public class EditorCapabilitiesResponseBody {

    /** 枚举名到可选值 */
    private final Map<String, List<String>> enums;

    /**
     * 上限名到数值
     * <li>与保存时的静态校验一致。</li>
     */
    private final Map<String, Integer> limits;

    /** 已注册函数 */
    private final List<RegisteredFunctionDetail> functions;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public EditorCapabilitiesResponseBody(
        @JsonProperty("enums") Map<String, List<String>> enums,
        @JsonProperty("limits") Map<String, Integer> limits,
        @JsonProperty("functions") List<RegisteredFunctionDetail> functions) {
        this.enums = enums;
        this.limits = limits;
        this.functions = functions == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(functions));
    }

    /** 枚举名到可选值 */
    public Map<String, List<String>> getEnums() {
        return enums;
    }

    /** 上限名到数值 */
    public Map<String, Integer> getLimits() {
        return limits;
    }

    /** 已注册函数 */
    public List<RegisteredFunctionDetail> getFunctions() {
        return functions;
    }
}
