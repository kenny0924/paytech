package com.paytech.channel.connector.api.externalapi.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 一个注册函数的签名。 */
public class RegisteredFunctionDetail {

    /** 函数编码 */
    private final String functionCode;

    /**
     * 函数类别
     * <li>INITIALIZER 可读取外部系统，只能出现在流程初始化项；PURE 无外部 I/O。</li>
     */
    private final String kind;

    /**
     * 参数声明
     * <li>顺序即展示顺序。</li>
     */
    private final List<ScriptParameterDetail> params;

    /** 返回值说明 */
    private final String returns;

    /**
     * 返回对象的一级属性及值类型
     * <li>纯函数为空对象。</li>
     */
    private final Map<String, String> returnRoots;

    /**
     * 可使用的位置
     * <li>FLOW_INITIALIZER、FLOW_NODE、FLOW_RETURN、API_OUTPUT。</li>
     */
    private final List<String> stages;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RegisteredFunctionDetail(
        @JsonProperty("functionCode") String functionCode,
        @JsonProperty("kind") String kind,
        @JsonProperty("params") List<ScriptParameterDetail> params,
        @JsonProperty("returns") String returns,
        @JsonProperty("returnRoots") Map<String, String> returnRoots,
        @JsonProperty("stages") List<String> stages) {
        this.functionCode = functionCode;
        this.kind = kind;
        this.params = params == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(params));
        this.returns = returns;
        this.returnRoots = returnRoots;
        this.stages = stages == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(stages));
    }

    /** 函数编码 */
    public String getFunctionCode() {
        return functionCode;
    }

    /** 函数类别 */
    public String getKind() {
        return kind;
    }

    /** 参数声明 */
    public List<ScriptParameterDetail> getParams() {
        return params;
    }

    /** 返回值说明 */
    public String getReturns() {
        return returns;
    }

    /** 返回对象的一级属性及值类型 */
    public Map<String, String> getReturnRoots() {
        return returnRoots;
    }

    /** 可使用的位置 */
    public List<String> getStages() {
        return stages;
    }
}
