package com.paytech.platform.support.integration.domainservice.execution;

import java.util.*;

/**
 * 公共脚本与状态规则读取的 HTTP 工作对象 http.*（原子 API 结构第 6、7 节）。
 *
 * <p>
 * REQUEST 阶段可读写 requestHeaders、requestBody；RESPONSE 阶段只读已发送的请求与状态码， 可读写 responseHeaders、responseBody 的工作副本，responseRaw
 * 为原始响应文本供验签。 不在可写集合中的属性不能被替换，只读部分是不可变副本，脚本写入会失败。
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
    private boolean sealed;

    private HttpWork(Set<String> writable) {
        this.writable = writable;
    }

    /** REQUEST 阶段：可读写请求 Header 与请求体。 */
    public static HttpWork request(Map<String, Object> headers, Object body) {
        var work = new HttpWork(Set.of(REQUEST_HEADERS, REQUEST_BODY));
        var mutableHeaders = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        mutableHeaders.putAll(headers);
        work.put(REQUEST_HEADERS, mutableHeaders);
        work.put(REQUEST_BODY, body);
        work.sealed = true;
        return work;
    }

    /** RESPONSE 阶段：已发送的请求只读，响应 Header 与响应体为可写工作副本。 */
    public static HttpWork response(HttpWork sent, int status, Map<String, String> headers, Object body, String raw) {
        var work = new HttpWork(Set.of(RESPONSE_HEADERS, RESPONSE_BODY));
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

    /** 状态规则与输出读取的只读视图。 */
    public Map<String, Object> view() {
        return readOnly(this) instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    @Override
    public Object put(String key, Object value) {
        if (sealed && !writable.contains(key)) {
            throw new UnsupportedOperationException("http." + key + " 在当前阶段不可写");
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
