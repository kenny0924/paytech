package com.paytech.platform.support.integration.domainservice.execution;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.ExecutionScript;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionProfile;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 公共脚本运行（原子 API 结构第 6 节）：按脚本剖面执行，环境只有 http 工作对象与脚本参数 params。
 *
 * <p>
 * 脚本参数的取值只能读原子 API 的调用参数 params.* 或常量；受控句柄原样传给脚本，只能交给注册函数。 任何失败都是节点 ERROR，异常信息只含脚本编码。
 */
@Component
@AllArgsConstructor
public class ScriptRuntime {

    /** 脚本与原子 API 环境中调用参数的变量名。 */
    public static final String PARAMS = "params";

    /** 脚本环境中 HTTP 工作对象的变量名。 */
    public static final String HTTP = "http";

    private final ExpressionEngine expressionEngine;
    private final ValueResolver valueResolver;

    /**
     * 执行一个脚本。
     *
     * @param script
     *            脚本快照
     * @param bindings
     *            原子 API 定义中该脚本引用的参数取值
     * @param apiParams
     *            原子 API 调用参数
     * @param http
     *            当前阶段的工作对象
     */
    public void run(ExecutionScript script, JsonNode bindings, Map<String, Object> apiParams, HttpWork http) {
        var environment = new HashMap<String, Object>();
        environment.put(HTTP, http);
        environment.put(PARAMS, parameters(script, bindings, apiParams));
        try {
            expressionEngine.compile(ExpressionProfile.SCRIPT, script.getSourceCode()).execute(environment);
        } catch (RuntimeException failure) {
            throw new ExecutionErrorException("公共脚本执行失败：" + script.getCode(), failure);
        }
    }

    /** 按脚本参数声明取值，环境只有原子 API 调用参数。 */
    Map<String, Object> parameters(ExecutionScript script, JsonNode bindings, Map<String, Object> apiParams) {
        Map<String, Object> environment = Map.of(PARAMS, apiParams);
        var result = new LinkedHashMap<String, Object>();
        for (ParameterDeclaration parameter : script.getParameters()) {
            JsonNode binding = bindings == null ? null : bindings.get(parameter.getName());
            String location = "scripts." + script.getCode() + ".params." + parameter.getName();
            if (binding == null) {
                if (parameter.isRequired()) {
                    throw new ExecutionErrorException("缺少必填脚本参数：" + location);
                }
                continue;
            }
            Object value =
                valueResolver.resolve(binding, environment, parameter.getDataType(), parameter.isRequired(), location);
            if (value != null) {
                result.put(parameter.getName(), value);
            }
        }
        return result;
    }
}
