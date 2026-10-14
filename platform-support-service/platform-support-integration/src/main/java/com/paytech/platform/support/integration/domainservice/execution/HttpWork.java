package com.paytech.platform.support.integration.domainservice.execution;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;

/**
 * 公共脚本与状态规则读取的 HTTP 工作对象 http.*（原子 API 结构第 6、7 节）。
 *
 * <p>
 * REQUEST 阶段可读写 requestHeaders、requestBody；RESPONSE 阶段只读已发送的请求与状态码， 可读写 responseHeaders、responseBody 的工作副本，responseRaw
 * 为原始响应文本供验签。 不在可写集合中的属性不能被替换，只读部分是不可变副本，脚本写入会失败。
 *
 * <p>
 * REQUEST 阶段可由脚本函数 sealJsonBody 锁定请求体（原子 API 结构第 6 节）：按本原子 API 的 JSON 编码规则序列化一次，此后请求体只读，
 * 传输层原样发送锁定的字节，签名覆盖的就是实际发送的内容。
 */
public final class HttpWork extends LinkedHashMap<String, Object> {

    /** 请求 Header，名称大小写不敏感。 */
    public static final String REQUEST_HEADERS = "requestHeaders";

    /** 请求体（编码前的对象）。 */
    public static final String REQUEST_BODY = "requestBody";

    /** 响应状态码。 */
    public static final String RESPONSE_STATUS = "responseStatus";

    /** 响应 Header，名称大小写不敏感。 */
    public static final String RESPONSE_HEADERS = "responseHeaders";

    /** 解码后的响应体。 */
    public static final String RESPONSE_BODY = "responseBody";

    /** 原始响应文本（UTF-8）。 */
    public static final String RESPONSE_RAW = "responseRaw";

    private final Set<String> writable;
    private final Function<Object, byte[]> jsonEncoder;
    private boolean sealed;
    private byte[] sealedJsonBody;

    private HttpWork(Set<String> writable, Function<Object, byte[]> jsonEncoder) {
        this.writable = writable;
        this.jsonEncoder = jsonEncoder;
    }

    /**
     * REQUEST 阶段：可读写请求 Header 与请求体。
     *
     * @param headers 组装后的请求 Header
     * @param body 组装后的请求体
     * @param jsonEncoder 本原子 API 的 JSON 请求体编码；requestEncoding 不是 JSON 时为 null，此时不能锁定请求体
     * @return 工作对象
     */
    public static HttpWork request(Map<String, Object> headers, Object body, Function<Object, byte[]> jsonEncoder) {
        var work = new HttpWork(Set.of(REQUEST_HEADERS, REQUEST_BODY), jsonEncoder);
        var mutableHeaders = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        mutableHeaders.putAll(headers);
        work.put(REQUEST_HEADERS, mutableHeaders);
        work.put(REQUEST_BODY, body);
        work.sealed = true;
        return work;
    }

    /** RESPONSE 阶段：已发送的请求只读，响应 Header 与响应体为可写工作副本。 */
    public static HttpWork response(HttpWork sent, int status, Map<String, String> headers, Object body, String raw) {
        var work = new HttpWork(Set.of(RESPONSE_HEADERS, RESPONSE_BODY), null);
        work.put(REQUEST_HEADERS, readOnly(sent.get(REQUEST_HEADERS)));
        work.put(REQUEST_BODY, readOnly(sent.get(REQUEST_BODY)));
        work.put(RESPONSE_STATUS, (long)status);
        var mutableHeaders = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        mutableHeaders.putAll(headers);
        work.put(RESPONSE_HEADERS, mutableHeaders);
        work.put(RESPONSE_BODY, body);
        work.put(RESPONSE_RAW, raw);
        work.sealed = true;
        return work;
    }

    /**
     * 按 JSON 编码规则序列化并锁定请求体，之后请求体只读。只能在 JSON 编码的 REQUEST 阶段调用一次。
     *
     * @return 锁定字节的 UTF-8 文本，即实际发送的请求体
     */
    public String sealJsonBody() {
        if (jsonEncoder == null) {
            throw new ExecutionErrorException("只能在 JSON 编码原子 API 的 REQUEST 阶段锁定请求体");
        }
        if (sealedJsonBody != null) {
            throw new ExecutionErrorException("请求体已锁定");
        }
        Object body = get(REQUEST_BODY);
        sealedJsonBody = jsonEncoder.apply(body);
        super.put(REQUEST_BODY, readOnly(body));
        return new String(sealedJsonBody, StandardCharsets.UTF_8);
    }

    /** 锁定的请求体字节；未锁定为 null。 */
    public byte[] sealedJsonBody() {
        return sealedJsonBody == null ? null : sealedJsonBody.clone();
    }

    /** 状态规则与输出读取的只读视图。 */
    public Map<String, Object> view() {
        return readOnly(this) instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    @Override
    public Object put(String key, Object value) {
        if (sealed && !writable.contains(key)) {
            throw new UnsupportedOperationException("http." + key + " 在当前阶段不可写");
        }
        if (sealedJsonBody != null && REQUEST_BODY.equals(key)) {
            throw new UnsupportedOperationException("请求体已锁定，不能再修改");
        }
        return super.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        if (sealed) {
            throw new UnsupportedOperationException("http 工作对象不能删除属性");
        }
        return super.remove(key);
    }

    /** 递归复制为不可变结构；Header 映射保持大小写不敏感。 */
    public static Object readOnly(Object value) {
        if (value instanceof TreeMap<?, ?> headers) {
            var copy = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
            headers.forEach((key, item) -> copy.put(String.valueOf(key), readOnly(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), readOnly(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<Object>(list.size());
            list.forEach(item -> copy.add(readOnly(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>)map;
    }
}
