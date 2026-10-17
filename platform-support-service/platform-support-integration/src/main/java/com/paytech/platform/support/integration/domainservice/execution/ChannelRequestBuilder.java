package com.paytech.platform.support.integration.domainservice.execution;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.core.configuration.ExternalApiExecutionProperties;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.execution.*;
import com.paytech.platform.support.integration.domain.execution.JsonValues.SecretRendering;
import com.paytech.platform.support.integration.ext.outbound.ChannelRequest;

import lombok.AllArgsConstructor;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 原子 API 请求组装与编码（原子 API 结构第 4、5、7 节）：fixedValues 与节点 request 按字段定义取值， 分到 HEADER（大小写不敏感）、QUERY、PATH（按单个路径段编码）与
 * BODY（按点分路径组装对象）； REQUEST 脚本之后按 requestEncoding 编码，拼出受控出站地址并计算超时。 cardFields 声明的卡字段只接受卡值句柄，
 * 只在 JSON 发送字节中展开为原值（PayerMax 卡直付对接技术方案第 3 节）。credentialFields 声明的凭据字段只接受受控句柄，同样只在 JSON
 * 发送字节中展开；请求体其他位置不能出现受控句柄（Toss Pay 接入技术方案 T1）。
 */
@Component
@AllArgsConstructor
public class ChannelRequestBuilder {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]{0,63})}");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build();

    private final ValueResolver valueResolver;
    private final ExternalApiExecutionProperties properties;

    /**
     * 按字段定义取值并分组。
     *
     * @param api
     *            原子 API 快照
     * @param request
     *            节点 request 取值，键为 &lt;LOCATION&gt;:&lt;path&gt;
     * @param environment
     *            节点取值环境（流程三区）
     * @return 分组后的请求值
     */
    public AssembledRequest assemble(ExecutionApi api, JsonNode request, Map<String, Object> environment) {
        Map<String, JsonNode> fixed = normalize(api.getDefinition().path("fixedValues"));
        Map<String, JsonNode> assigned = normalize(request);
        var cardKeys = api.cardFieldKeys();
        var credentialKeys = api.credentialFieldKeys();
        var result = new AssembledRequest();
        for (FieldDefinition field : api.getRequestFields()) {
            String key = key(field.getLocation(), field.getPath());
            boolean constant = fixed.containsKey(key);
            JsonNode binding = constant ? fixed.get(key) : assigned.get(key);
            if (binding == null || field.getPath().contains("[]")) {
                if (field.isRequired() && !field.getPath().contains("[]")) {
                    throw new ExecutionErrorException("必填请求字段未赋值：" + key);
                }
                continue;
            }
            String dataType = cardKeys.contains(key) ? ValueResolver.CARD
                : credentialKeys.contains(key) ? ValueResolver.PROTECTED_HANDLE : field.getDataType().getCode();
            Object value = valueResolver.resolve(binding, constant ? Map.of() : environment, dataType, field.isRequired(),
                "request." + key);
            if (value != null) {
                result.put(field, value);
            }
        }
        checkBody(api, result.body);
        return result;
    }

    /** BODY 对象满足字段结构（必填、类型、未声明属性）。 */
    void checkBody(ExecutionApi api, Map<String, Object> body) {
        try {
            FieldSchema.of(api.getRequestFields()).check(body, "");
        } catch (FieldSchemaViolation violation) {
            throw new ExecutionErrorException("请求体不满足字段结构：" + violation.getPath());
        }
    }

    /**
     * 组装最终请求：地址、Header 明文、按编码方式生成的请求体与超时。
     *
     * @param snapshot
     *            本次执行快照（出站地址）
     * @param api
     *            原子 API 快照
     * @param assembled
     *            组装结果（PATH、QUERY）
     * @param headers
     *            REQUEST 脚本之后的请求 Header
     * @param body
     *            REQUEST 脚本之后的请求体
     * @param sealedJsonBody
     *            REQUEST 脚本以 sealJsonBody 锁定的请求体字节，未锁定为 null；锁定时原样发送
     * @param deadline
     *            流程剩余预算
     * @return 最终请求
     */
    public ChannelRequest build(ExecutionSnapshot snapshot, ExecutionApi api, AssembledRequest assembled,
        Map<String, Object> headers, Object body, byte[] sealedJsonBody, Deadline deadline) {
        JsonNode transport = api.getDefinition().path("transport");
        var finalHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> finalHeaders.put(name, headerText(name, value)));
        checkCredentialPlacement(api, body, "");
        byte[] bytes = sealedJsonBody != null ? sealed(sealedJsonBody, finalHeaders) : encode(api, body, finalHeaders);
        URI uri = uri(snapshot, transport, assembled);
        long remaining = deadline.remainingMillis();
        if (remaining <= 0) {
            throw new ExecutionErrorException("流程预算已耗尽，未发送请求");
        }
        return new ChannelRequest(uri, transport.path("method").asString(), finalHeaders, bytes,
            Duration.ofMillis(Math.min(transport.path("connectTimeoutMs").asLong(2000), remaining)),
            Duration.ofMillis(Math.min(transport.path("responseTimeoutMs").asLong(10000), remaining)));
    }

    /** REQUEST 脚本之后的请求体中，受控句柄只能位于 credentialFields 声明的字段。 */
    @SuppressWarnings("unchecked")
    static void checkCredentialPlacement(ExecutionApi api, Object value, String path) {
        if (value instanceof ProtectedHandle && !api.credentialFieldKeys().contains("BODY:" + path)) {
            throw new ExecutionErrorException("受控句柄只能写入原子 API 声明的凭据字段：BODY:" + path);
        }
        if (value instanceof Map<?, ?> map) {
            ((Map<String, Object>)map).forEach((name, item) ->
                checkCredentialPlacement(api, item, path.isEmpty() ? name : path + "." + name));
        }
        if (value instanceof java.util.List<?> list) {
            list.forEach(item -> checkCredentialPlacement(api, item, path + "[]"));
        }
    }

    /**
     * 按本原子 API 的 JSON 规则编码请求体：decimalEncoding 为 NUMBER 时 DECIMAL 写成 JSON 数字（不用指数），否则写成十进制字符串。
     *
     * @param api 原子 API 快照
     * @param body 请求体
     * @return UTF-8 字节
     */
    public byte[] jsonBody(ExecutionApi api, Object body) {
        return JSON.writeValueAsBytes(JsonValues.toJson(body, api.isDecimalAsNumber(), SecretRendering.WIRE));
    }

    /**
     * 与 {@link #jsonBody} 相同的编码，但卡值与凭据字段写成遮蔽文本；只用于执行轨迹与沙箱验证证据。
     *
     * @param api 原子 API 快照
     * @param body 请求体
     * @return UTF-8 字节
     */
    public byte[] maskedJsonBody(ExecutionApi api, Object body) {
        return JSON.writeValueAsBytes(JsonValues.toJson(body, api.isDecimalAsNumber(), SecretRendering.MASKED));
    }

    /** 锁定的 JSON 请求体原样发送；缺少 Content-Type 时补 application/json。 */
    static byte[] sealed(byte[] sealedJsonBody, Map<String, String> headers) {
        headers.putIfAbsent("Content-Type", "application/json");
        return sealedJsonBody;
    }

    /** JSON、FORM_URLENCODED 或无请求体；缺少 Content-Type 时按编码补默认值。 */
    byte[] encode(ExecutionApi api, Object body, Map<String, String> headers) {
        String encoding = api.getDefinition().path("requestEncoding").asString();
        boolean empty = body == null || body instanceof Map<?, ?> map && map.isEmpty();
        if ("NONE".equals(encoding) || empty) {
            if (!empty) {
                throw new ExecutionErrorException("requestEncoding 为 NONE 时不能有请求体");
            }
            return null;
        }
        if ("FORM_URLENCODED".equals(encoding)) {
            headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
            return form(body).getBytes(StandardCharsets.UTF_8);
        }
        headers.putIfAbsent("Content-Type", "application/json");
        return jsonBody(api, body);
    }

    /** 只接受一层标量对象。 */
    static String form(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            throw new ExecutionErrorException("FORM_URLENCODED 请求体必须是对象");
        }
        var parts = new java.util.ArrayList<String>();
        map.forEach((name, value) -> parts.add(encode(String.valueOf(name)) + "=" + encode(scalarText(value))));
        return String.join("&", parts);
    }

    /** 基础地址 + 路径模板（变量按单个路径段编码）+ 查询串；协议必须在允许列表中。 */
    URI uri(ExecutionSnapshot snapshot, JsonNode transport, AssembledRequest assembled) {
        String base = snapshot.getEndpoints().get(transport.path("endpointCode").asString());
        URI baseUri = base == null ? null : URI.create(base);
        if (baseUri == null || !properties.getAllowedSchemes().contains(baseUri.getScheme())) {
            throw new ExecutionErrorException("出站地址不存在或协议不允许：" + transport.path("endpointCode").asString());
        }
        Matcher matcher = PATH_VARIABLE.matcher(transport.path("pathTemplate").asString());
        var path = new StringBuilder();
        while (matcher.find()) {
            Object value = assembled.path.get(matcher.group(1));
            String text = value == null ? null : scalarText(value);
            if (text == null || text.isEmpty() || text.equals(".") || text.equals("..")) {
                throw new ExecutionErrorException("路径变量缺失或不是合法路径段：" + matcher.group(1));
            }
            matcher.appendReplacement(path, Matcher.quoteReplacement(encode(text)));
        }
        matcher.appendTail(path);
        var query = new java.util.ArrayList<String>();
        assembled.query.forEach((name, value) -> query.add(encode(name) + "=" + encode(scalarText(value))));
        String prefix = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(prefix + path + (query.isEmpty() ? "" : "?" + String.join("&", query)));
    }

    /** Header 值：受控句柄在此取出明文发送，其余必须是标量。 */
    static String headerText(String name, Object value) {
        if (value instanceof ProtectedHandle handle) {
            return handle.apply(secret -> secret);
        }
        if (value == null) {
            throw new ExecutionErrorException("请求 Header 值为空：" + name);
        }
        return scalarText(value);
    }

    /** 标量文本：字符串原样，布尔与整数按十进制，小数不使用科学计数法。 */
    static String scalarText(Object value) {
        return switch (value) {
            case String text -> text;
            case Boolean flag -> flag.toString();
            case Long number -> number.toString();
            case Integer number -> number.toString();
            case BigDecimal decimal -> decimal.toPlainString();
            default -> throw new ExecutionErrorException("该位置只能使用标量值");
        };
    }

    /** UTF-8 百分号编码，空格编码为 %20。 */
    static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 请求取值键 &lt;LOCATION&gt;:&lt;path&gt;，HEADER 名称小写。 */
    static String key(ApiFieldLocation location, String path) {
        return location.getCode() + ":" + (location == ApiFieldLocation.HEADER ? path.toLowerCase(Locale.ROOT) : path);
    }

    /** 把取值对象的键规范为 {@link #key}。 */
    static Map<String, JsonNode> normalize(JsonNode values) {
        var result = new LinkedHashMap<String, JsonNode>();
        if (values == null || !values.isObject()) {
            return result;
        }
        for (var entry : values.properties()) {
            String[] parts = entry.getKey().split(":", 2);
            ApiFieldLocation location = ApiFieldLocation.fromCode(parts[0]);
            if (location != null && parts.length == 2) {
                result.put(key(location, parts[1]), entry.getValue());
            }
        }
        return result;
    }

    /** 分组后的请求值。 */
    public static final class AssembledRequest {

        /** HEADER，名称大小写不敏感。 */
        private final Map<String, Object> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        /** QUERY，保持字段定义顺序。 */
        private final Map<String, Object> query = new LinkedHashMap<>();

        /** PATH 变量。 */
        private final Map<String, Object> path = new LinkedHashMap<>();

        /** BODY 对象。 */
        private final Map<String, Object> body = new LinkedHashMap<>();

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public Map<String, Object> getBody() {
            return body;
        }

        /** 按字段位置放入值；BODY 按点分路径创建中间对象。 */
        @SuppressWarnings("unchecked")
        void put(FieldDefinition field, Object value) {
            switch (field.getLocation()) {
                case HEADER -> headers.put(field.getPath(), value);
                case QUERY -> query.put(field.getPath(), value);
                case PATH -> path.put(field.getPath(), value);
                case BODY -> {
                    String[] segments = field.getPath().split("\\.");
                    Map<String, Object> current = body;
                    for (int index = 0; index < segments.length - 1; index++) {
                        Object next =
                            current.computeIfAbsent(segments[index], name -> new LinkedHashMap<String, Object>());
                        if (!(next instanceof LinkedHashMap<?, ?>)) {
                            throw new ExecutionErrorException("请求体路径与已赋值的上级冲突：" + field.getPath());
                        }
                        current = (Map<String, Object>)next;
                    }
                    current.put(segments[segments.length - 1], value);
                }
            }
        }
    }
}
