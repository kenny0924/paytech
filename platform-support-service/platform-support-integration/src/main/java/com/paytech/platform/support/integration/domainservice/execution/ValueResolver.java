package com.paytech.platform.support.integration.domainservice.execution;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.ProtectedHandle;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionProfile;
import com.paytech.platform.support.integration.domainservice.function.PureFunction;

import tools.jackson.databind.JsonNode;

/**
 * 取值绑定（PATH / EXPRESSION / FUNCTION）的运行时求值，按流程结构第 5 节处理缺失与类型。
 *
 * <p>
 * 没有 nullable 定义，null 与缺失同样表示“没有值”：必填目标报 ERROR，非必填目标省略。 表达式或函数异常、类型不符一律 ERROR，不隐式转换或舍入。环境由调用方提供（流程三区或原子 API 的
 * http/params）。
 */
@Component
public class ValueResolver {

    /** FUNCTION 的 result 表达式中函数返回值的变量名。 */
    public static final String RESULT = "result";

    private final ExpressionEngine expressionEngine;
    private final Map<String, PureFunction> functions;

    public ValueResolver(ExpressionEngine expressionEngine, List<PureFunction> functions) {
        this.expressionEngine = expressionEngine;
        this.functions =
            functions.stream().collect(Collectors.toUnmodifiableMap(PureFunction::code, Function.identity()));
    }

    /**
     * 求值并检查必填。
     *
     * @param binding
     *            取值绑定
     * @param environment
     *            只读环境
     * @param dataType
     *            目标类型代码
     * @param required
     *            目标是否必填
     * @param location
     *            配置位置，只用于错误说明
     * @return 值；非必填且没有值时为 null
     * @throws ExecutionErrorException
     *             必填缺失、求值失败或类型不符
     */
    public Object resolve(JsonNode binding, Map<String, Object> environment, String dataType, boolean required,
        String location) {
        Object value = resolve(binding, environment, dataType, location);
        if (value == null && required) {
            throw new ExecutionErrorException("必填取值缺失：" + location);
        }
        return value;
    }

    /** 求值并检查类型；没有值返回 null。 */
    Object resolve(JsonNode binding, Map<String, Object> environment, String dataType, String location) {
        Object value = switch (binding.path("kind").asString("")) {
            case "PATH" -> path(environment, binding.path("path").asString());
            case "EXPRESSION" -> evaluate(binding.path("expression").asString(), environment, location);
            case "FUNCTION" -> function(binding, environment, location);
            default -> throw new ExecutionErrorException("取值方式不受支持：" + location);
        };
        if (value != null && !matches(value, dataType)) {
            throw new ExecutionErrorException("取值类型与目标不符：" + location + "，期望 " + dataType);
        }
        return value;
    }

    /** 点分路径逐级读取 Map；任一段不存在或不是对象时没有值。 */
    public static Object path(Map<String, Object> environment, String path) {
        Object current = environment;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    /** 只读剖面求值；任何异常都是 ERROR。 */
    Object evaluate(String expression, Map<String, Object> environment, String location) {
        try {
            return expressionEngine.compile(ExpressionProfile.READ_ONLY, expression).execute(environment);
        } catch (RuntimeException exception) {
            throw new ExecutionErrorException("表达式求值失败：" + location, exception);
        }
    }

    /** 纯函数：按签名解析参数、调用实现，再用 result 表达式取值。 */
    Object function(JsonNode binding, Map<String, Object> environment, String location) {
        String code = binding.path("function").asString();
        var signature =
            RegisteredFunctionCatalog.find(code).filter(item -> RegisteredFunctionCatalog.PURE.equals(item.getKind()));
        PureFunction implementation = functions.get(code);
        if (signature.isEmpty() || implementation == null) {
            throw new ExecutionErrorException("纯函数未注册：" + location);
        }
        var args = new LinkedHashMap<String, Object>();
        for (ParameterDeclaration parameter : signature.get().getArgs()) {
            JsonNode argument = binding.path("args").get(parameter.getName());
            String argumentLocation = location + ".args." + parameter.getName();
            Object value =
                argument == null ? null : resolve(argument, environment, parameter.getDataType(), argumentLocation);
            if (value == null && parameter.isRequired()) {
                throw new ExecutionErrorException("必填取值缺失：" + argumentLocation);
            }
            if (value != null) {
                args.put(parameter.getName(), value);
            }
        }
        Object result;
        try {
            result = implementation.apply(args);
        } catch (ExecutionErrorException exception) {
            throw new ExecutionErrorException("函数执行失败：" + location + "，" + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new ExecutionErrorException("函数执行失败：" + location, exception);
        }
        var resultEnvironment = new HashMap<String, Object>();
        resultEnvironment.put(RESULT, result);
        return evaluate(binding.path("result").asString(), resultEnvironment, location + ".result");
    }

    /** 目标类型匹配：不做任何转换；整数只接受 Long/Integer，小数只接受 BigDecimal，受控句柄只接受句柄本身。 */
    public static boolean matches(Object value, String dataType) {
        return switch (dataType) {
            case "STRING" -> value instanceof String;
            case "BOOLEAN" -> value instanceof Boolean;
            case "INTEGER" -> value instanceof Long || value instanceof Integer;
            case "DECIMAL" -> value instanceof BigDecimal;
            case "OBJECT" -> value instanceof Map<?, ?>;
            case "ARRAY" -> value instanceof List<?>;
            case "PROTECTED_HANDLE" -> value instanceof ProtectedHandle;
            default -> false;
        };
    }
}
