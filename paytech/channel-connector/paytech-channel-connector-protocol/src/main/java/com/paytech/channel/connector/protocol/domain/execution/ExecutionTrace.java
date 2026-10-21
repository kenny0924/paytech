package com.paytech.channel.connector.protocol.domain.execution;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 沙箱验证运行的执行轨迹（沙箱验证技术方案第 6 节）：配置版本与出站地址、初始化项、节点与渠道交换、出口。
 *
 * <p>
 * 脱敏：名称为 Authorization、Proxy-Authorization、Cookie、Set-Cookie 的 Header 以及值来自受控句柄的请求 Header 记为
 * [REDACTED]；签名类 Header 保留。每段报文最多保留 64 KiB，超出截断并标记；非 UTF-8 内容记录长度与前 4 KiB 的 Base64。
 * 初始化项不记录账号内容。只用于验证运行，正式执行不创建。
 */
public class ExecutionTrace {

    /** 单段报文最多保留的字节数。 */
    public static final int MAX_BODY_BYTES = 65_536;

    /** 脱敏占位。 */
    public static final String REDACTED = "[REDACTED]";

    static final int BINARY_PREVIEW_BYTES = 4096;
    static final Set<String> SECRET_HEADERS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");

    private Map<String, Object> configVersions;
    private final List<Map<String, Object>> endpoints = new ArrayList<>();
    private final List<Map<String, Object>> initializers = new ArrayList<>();
    private final Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
    private String returnId;

    /** 记录本次加载的配置版本与出站地址。 */
    public void loaded(Map<String, Object> versions, Map<String, String> baseUrls, Set<String> sandbox) {
        configVersions = versions;
        endpoints.clear();
        new TreeMap<>(baseUrls).forEach((code, url) -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("endpointCode", code);
            row.put("baseUrl", url);
            row.put("sandbox", sandbox.contains(code));
            endpoints.add(row);
        });
    }

    /** 记录一个初始化项的结果；不记录参数与返回内容。 */
    public void initializer(String id, String function, boolean succeeded, String reason) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("function", function);
        row.put("succeeded", succeeded);
        if (reason != null) {
            row.put("reason", reason);
        }
        initializers.add(row);
    }

    /**
     * 记录一次渠道交换。
     *
     * @param nodeId 节点 ID
     * @param request 已发送的请求（方法、地址、Header、请求体）
     * @param protectedHeaders 值来自受控句柄的请求 Header 名称
     * @param status 响应状态码；没有响应为 null
     * @param responseHeaders 响应 Header；没有响应为 null
     * @param responseBody 响应体；没有响应为 null
     * @param elapsedMs 耗时毫秒
     * @param error 没有响应时的固定原因
     */
    public void exchange(String nodeId, Exchange request, Set<String> protectedHeaders, Integer status,
        Map<String, String> responseHeaders, byte[] responseBody, long elapsedMs, String error) {
        var sent = new LinkedHashMap<String, Object>();
        sent.put("method", request.method());
        sent.put("url", request.uri().toString());
        sent.put("headers", headers(request.headers(), protectedHeaders));
        sent.put("body", body(request.body()));
        var exchange = new LinkedHashMap<String, Object>();
        exchange.put("request", sent);
        if (status != null) {
            var received = new LinkedHashMap<String, Object>();
            received.put("status", status);
            received.put("headers", headers(responseHeaders, Set.of()));
            received.put("body", body(responseBody));
            exchange.put("response", received);
        }
        exchange.put("elapsedMs", elapsedMs);
        if (error != null) {
            exchange.put("error", error);
        }
        node(nodeId).put("exchange", exchange);
    }

    /** 记录节点结论；在渠道交换之后调用。 */
    public void nodeResult(String nodeId, String integrationApi, String executeStatus, String reason, long elapsedMs) {
        var node = node(nodeId);
        node.put("integrationApi", integrationApi);
        node.put("executeStatus", executeStatus);
        if (reason != null) {
            node.put("reason", reason);
        }
        node.put("elapsedMs", elapsedMs);
    }

    /** 记录流程到达的出口。 */
    public void returned(String id) {
        returnId = id;
    }

    /** 到达的出口 ID；流程级失败时为 null。 */
    public String getReturnId() {
        return returnId;
    }

    /** 本次加载的配置版本；未加载为 null。 */
    public Map<String, Object> getConfigVersions() {
        return configVersions;
    }

    /** 本次加载的出站地址；未加载为空列表。 */
    public List<Map<String, Object>> getEndpoints() {
        return List.copyOf(endpoints);
    }

    /** 轨迹的 JSON 投影：initializers、nodes（按执行顺序）、returnId。 */
    public Map<String, Object> toMap() {
        var result = new LinkedHashMap<String, Object>();
        result.put("initializers", List.copyOf(initializers));
        result.put("nodes", List.copyOf(nodes.values()));
        result.put("returnId", returnId);
        return result;
    }

    Map<String, Object> node(String nodeId) {
        return nodes.computeIfAbsent(nodeId, id -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", id);
            return row;
        });
    }

    static Map<String, String> headers(Map<String, String> headers, Set<String> protectedHeaders) {
        var result = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        var protectedNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        protectedNames.addAll(protectedHeaders);
        headers.forEach((name, value) -> result.put(name,
            SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT)) || protectedNames.contains(name) ? REDACTED : value));
        return result;
    }

    static Map<String, Object> body(byte[] bytes) {
        var result = new LinkedHashMap<String, Object>();
        int length = bytes == null ? 0 : bytes.length;
        result.put("length", length);
        if (length == 0) {
            result.put("text", "");
            return result;
        }
        boolean truncated = length > MAX_BODY_BYTES;
        byte[] kept = truncated ? Arrays.copyOf(bytes, MAX_BODY_BYTES) : bytes;
        try {
            result.put("text", StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(kept)).toString());
        } catch (CharacterCodingException notText) {
            if (!truncated || !validPrefix(kept)) {
                result.put("base64", Base64.getEncoder()
                    .encodeToString(Arrays.copyOf(bytes, Math.min(length, BINARY_PREVIEW_BYTES))));
                return result;
            }
            result.put("text", new String(kept, StandardCharsets.UTF_8));
        }
        if (truncated) {
            result.put("truncated", true);
        }
        return result;
    }

    /** 截断点落在多字节字符中间时仍视为文本：去掉末尾最多 3 字节后可解码。 */
    static boolean validPrefix(byte[] kept) {
        for (int drop = 1; drop <= 3; drop++) {
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(kept, 0, kept.length - drop));
                return true;
            } catch (CharacterCodingException ignored) {
                // 继续去掉下一个字节
            }
        }
        return false;
    }

    /**
     * 已发送请求的可记录部分。
     *
     * @param method HTTP 方法
     * @param uri 完整地址
     * @param headers 最终 Header（含明文，由轨迹脱敏）
     * @param body 已编码请求体，无请求体为 null
     */
    public record Exchange(String method, URI uri, Map<String, String> headers, byte[] body) {
    }
}
