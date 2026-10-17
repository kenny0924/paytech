package com.paytech.platform.support.integration.domainservice.execution;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ExecuteStatus;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.execution.*;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionEngine;
import com.paytech.platform.support.integration.domainservice.expression.ExpressionProfile;
import com.paytech.platform.support.integration.ext.outbound.ChannelHttpClient;
import com.paytech.platform.support.integration.ext.outbound.ChannelRequest;
import com.paytech.platform.support.integration.ext.outbound.ChannelResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 原子 API 单次执行（原子 API 结构第 7 节）：调用参数 → 组装请求 → REQUEST 脚本 → 编码发送 → 解码 → RESPONSE 脚本 → 状态规则 → 响应字段校验 → 分支输出，结果写入
 * steps.&lt;节点&gt;。
 *
 * <p>
 * 状态规则全部为假固定为 ERROR；任何异常由流程引擎记为该节点 ERROR。状态规则与输出只读 http 与 不含受控句柄的 params。日志只记节点 ID、渠道 HTTP 状态与耗时，不记 Header 与报文。
 */
@Slf4j
@Component
@AllArgsConstructor
public class AtomicApiExecutor implements NodeRunner {

    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();

    private final ScriptRuntime scriptRuntime;
    private final ValueResolver valueResolver;
    private final ChannelHttpClient httpClient;
    private final ExpressionEngine expressionEngine;
    private final ChannelRequestBuilder requestBuilder;

    @Override
    public StepResult run(ExecutionSnapshot snapshot, ExecutionFlow.Node node, ExecutionContext context,
        Deadline deadline) {
        ExecutionApi api = snapshot.getApis().get(node.getIntegrationApi());
        Map<String, Object> environment = context.environment();
        Map<String, Object> params = params(api, node, environment);
        var assembled = requestBuilder.assemble(api, node.getRequest(), environment);
        HttpWork request = HttpWork.request(assembled.getHeaders(), assembled.getBody(),
            "JSON".equals(api.getDefinition().path("requestEncoding").asString())
                ? body -> requestBuilder.jsonBody(api, body) : null);
        runScripts(snapshot, api, ScriptExecutionStage.REQUEST, params, request);
        var channelRequest = requestBuilder.build(snapshot, api, assembled,
            castMap(request.get(HttpWork.REQUEST_HEADERS)), request.get(HttpWork.REQUEST_BODY),
            request.sealedJsonBody(), deadline);
        long started = System.nanoTime();
        byte[] traceBody =
            context.trace() == null ? null : traceBody(api, channelRequest, request.get(HttpWork.REQUEST_BODY));
        ChannelResponse response = send(channelRequest, traceBody, node.getId(),
            castMap(request.get(HttpWork.REQUEST_HEADERS)), context.trace(), started);
        log.info("外部 API 渠道响应 standardApiCode={} integrationCode={} nodeId={} httpStatus={} elapsedMs={}",
            snapshot.getStandardApiCode(), snapshot.getIntegrationCode(), node.getId(), response.getStatus(),
            (System.nanoTime() - started) / 1_000_000L);
        HttpWork received = HttpWork.response(request, response.getStatus(), response.getHeaders(),
            decode(api, response), new String(response.getBody(), StandardCharsets.UTF_8));
        runScripts(snapshot, api, ScriptExecutionStage.RESPONSE, params, received);
        return result(api, received, params);
    }

    /** 执行轨迹记录的请求体：含卡值或受控句柄时用同一编码规则重新生成遮蔽副本，否则即发送字节。 */
    byte[] traceBody(ExecutionApi api, ChannelRequest request, Object body) {
        return JsonValues.containsCard(body) || JsonValues.containsHandle(body) ? requestBuilder.maskedJsonBody(api, body)
            : request.getBody();
    }

    /** 发送请求；沙箱验证运行同时记录脱敏后的渠道交换（无论成功或失败），请求体卡值已遮蔽。 */
    ChannelResponse send(ChannelRequest request, byte[] traceBody, String nodeId, Map<String, Object> headers,
        ExecutionTrace trace, long started) {
        if (trace == null) {
            return httpClient.send(request);
        }
        var exchange = new ExecutionTrace.Exchange(request.getMethod(), request.getUri(), request.getHeaders(), traceBody);
        Set<String> protectedHeaders = headers.entrySet().stream()
            .filter(entry -> entry.getValue() instanceof ProtectedHandle).map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        try {
            ChannelResponse response = httpClient.send(request);
            trace.exchange(nodeId, exchange, protectedHeaders, response.getStatus(), response.getHeaders(),
                response.getBody(), (System.nanoTime() - started) / 1_000_000L, null);
            return response;
        } catch (RuntimeException failure) {
            trace.exchange(nodeId, exchange, protectedHeaders, null, null, null,
                (System.nanoTime() - started) / 1_000_000L, FlowEngine.reason(failure));
            throw failure;
        }
    }

    /** 状态规则决定分支，再校验响应字段并映射分支输出。 */
    StepResult result(ExecutionApi api, HttpWork received, Map<String, Object> params) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put(ScriptRuntime.HTTP, received.view());
        view.put(ScriptRuntime.PARAMS, withoutHandles(params));
        ExecuteStatus status = status(api, view);
        if (status == ExecuteStatus.ERROR) {
            return StepResult.error();
        }
        checkResponseFields(api, received, status == ExecuteStatus.SUCCESS);
        return new StepResult(status, outputs(api, status, view));
    }

    /** 节点 params 按原子 API 声明取值；受控句柄以 PATH 读取初始化结果中的凭据。 */
    Map<String, Object> params(ExecutionApi api, ExecutionFlow.Node node, Map<String, Object> environment) {
        var result = new LinkedHashMap<String, Object>();
        for (JsonNode declaration : api.getDefinition().path("params")) {
            String name = declaration.path("name").asString();
            JsonNode binding = node.getParams().get(name);
            boolean required = declaration.path("required").asBoolean();
            if (binding == null) {
                if (required) {
                    throw new ExecutionErrorException("缺少必填调用参数：" + node.getId() + ".params." + name);
                }
                continue;
            }
            Object value = valueResolver.resolve(binding, environment, declaration.path("dataType").asString(),
                required, "nodes." + node.getId() + ".params." + name);
            if (value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    /** 按定义顺序执行本阶段的脚本。 */
    void runScripts(ExecutionSnapshot snapshot, ExecutionApi api, ScriptExecutionStage stage,
        Map<String, Object> params, HttpWork http) {
        for (JsonNode use : api.getDefinition().path("scripts")) {
            if (stage.getCode().equals(use.path("stage").asString())) {
                scriptRuntime.run(snapshot.getScripts().get(use.path("scriptCode").asString()), use.path("params"),
                    params, http);
            }
        }
    }

    /**
     * JSON 按严格规则解析（空响应体为 nil，小数精确解析为 BigDecimal）；NONE 不解析，留给 RESPONSE 脚本。 decimalEncoding 为 NUMBER
     * 时，已声明为 DECIMAL 的 BODY 响应字段中的 JSON 整数转为 BigDecimal。
     */
    Object decode(ExecutionApi api, ChannelResponse response) {
        if (!"JSON".equals(api.getDefinition().path("responseDecoding").asString()) || response.getBody().length == 0) {
            return null;
        }
        Object body;
        try {
            body = JsonValues.toJava(JSON.readTree(response.getBody()));
        } catch (JacksonException failure) {
            throw new ExecutionErrorException("渠道响应不是合法 JSON");
        }
        if (api.isDecimalAsNumber()) {
            api.getResponseFields().stream().filter(field -> field.getLocation() == ApiFieldLocation.BODY
                && field.getDataType() == ApiFieldDataType.DECIMAL)
                .forEach(field -> integerToDecimal(body, field.getPath().split("\\."), 0));
        }
        return body;
    }

    /** 沿字段路径（数组段以 [] 结尾）把末端的 JSON 整数改为 BigDecimal；路径不存在时不处理。 */
    static void integerToDecimal(Object node, String[] segments, int index) {
        if (!(node instanceof Map<?, ?> map)) {
            return;
        }
        boolean array = segments[index].endsWith("[]");
        String name = array ? segments[index].substring(0, segments[index].length() - 2) : segments[index];
        boolean last = index == segments.length - 1;
        Object value = map.get(name);
        if (!array) {
            if (!last) {
                integerToDecimal(value, segments, index + 1);
            } else if (decimal(value) != null) {
                castMap(map).put(name, decimal(value));
            }
            return;
        }
        if (!(value instanceof List<?> items)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>)items;
        for (int position = 0; position < list.size(); position++) {
            if (!last) {
                integerToDecimal(list.get(position), segments, index + 1);
            } else if (decimal(list.get(position)) != null) {
                list.set(position, decimal(list.get(position)));
            }
        }
    }

    /** JSON 整数对应的 BigDecimal；不是整数为 null。 */
    static BigDecimal decimal(Object value) {
        return switch (value) {
            case Long number -> BigDecimal.valueOf(number);
            case BigInteger number -> new BigDecimal(number);
            case null, default -> null;
        };
    }

    /** 第一条为真的规则决定状态；全部为假为 ERROR；条件非布尔或异常同样为 ERROR。 */
    ExecuteStatus status(ExecutionApi api, Map<String, Object> view) {
        for (JsonNode rule : api.getDefinition().path("statusRules")) {
            Object matched;
            try {
                matched = expressionEngine.compile(ExpressionProfile.READ_ONLY, rule.path("condition").asString())
                    .execute(view);
            } catch (RuntimeException failure) {
                throw new ExecutionErrorException("状态规则求值失败：" + api.getCode(), failure);
            }
            if (!(matched instanceof Boolean value)) {
                throw new ExecutionErrorException("状态规则结果不是布尔值：" + api.getCode());
            }
            if (value) {
                return ExecuteStatus.fromCode(rule.path("executeStatus").asString());
            }
        }
        return ExecuteStatus.ERROR;
    }

    /** 响应字段：出现即校验类型；必填只在 SUCCESS 且上级存在时校验（原子 API 结构第 7.2 节）。 */
    void checkResponseFields(ExecutionApi api, HttpWork received, boolean success) {
        Map<String, Object> body =
            received.get(HttpWork.RESPONSE_BODY) instanceof Map<?, ?> map ? castMap(map) : Map.of();
        Map<String, Object> headers = castMap(received.get(HttpWork.RESPONSE_HEADERS));
        for (FieldDefinition field : api.getResponseFields()) {
            if (field.getPath().contains("[]")) {
                continue;
            }
            boolean header = field.getLocation() == ApiFieldLocation.HEADER;
            Object value = header ? headers.get(field.getPath()) : ValueResolver.path(body, field.getPath());
            if (value != null && !header && !ValueResolver.matches(value, field.getDataType().getCode())) {
                throw new ExecutionErrorException("响应字段类型不符：" + api.getCode() + "." + field.getPath());
            }
            if (value == null && success && field.isRequired() && (header || parentPresent(body, field.getPath()))) {
                throw new ExecutionErrorException("响应缺少必填字段：" + api.getCode() + "." + field.getPath());
            }
        }
    }

    /** 顶层字段或上级对象存在。 */
    static boolean parentPresent(Map<String, Object> body, String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || ValueResolver.path(body, path.substring(0, dot)) != null;
    }

    /** 按分支输出声明取值；必填缺失或类型不符即失败。 */
    Map<String, Object> outputs(ExecutionApi api, ExecuteStatus status, Map<String, Object> view) {
        var result = new LinkedHashMap<String, Object>();
        for (JsonNode output : api.getDefinition().path("outputs").path(status.getCode())) {
            String name = output.path("name").asString();
            Object value = valueResolver.resolve(output.path("value"), view, output.path("dataType").asString(),
                output.path("required").asBoolean(), api.getCode() + ".outputs." + status.getCode() + "." + name);
            if (value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    /** 状态规则与输出不能读取受控句柄。 */
    static Map<String, Object> withoutHandles(Map<String, Object> params) {
        var result = new LinkedHashMap<String, Object>();
        params.forEach((name, value) -> {
            if (!(value instanceof ProtectedHandle)) {
                result.put(name, value);
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>)value;
    }
}
