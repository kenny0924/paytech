package com.paytech.foundation.testsupport.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** 测试专用 HTTP 调用器，复用宿主客户端，不补 Header、不重试、不预判成功。 */
public final class HttpTestInvoker {
    private final RestTestClient client;

    /** 使用宿主已配置随机服务地址及传输策略的客户端。 */
    public HttpTestInvoker(RestTestClient client) {
        this.client = client;
    }

    /** 发送 GET 请求，路径和查询参数遵循宿主 URI 编码规则。 */
    public EntityExchangeResult<byte[]> get(String path, HttpHeaders headers) {
        return exchange(HttpMethod.GET, path, headers, null);
    }

    /** 发送 POST；DTO、原始 JSON 字符串和字节按宿主消息转换器处理。 */
    public EntityExchangeResult<byte[]> post(String path, HttpHeaders headers, Object body) {
        return exchange(HttpMethod.POST, path, headers, body);
    }

    /**
     * 发送一次请求并保留原始 HTTP 结果。
     *
     * @param method HTTP 方法
     * @param path 宿主服务路径及可选查询参数
     * @param headers 本次最终请求头，显式缺失或非法值不会修复
     * @param body 请求体，null 表示不发送内容
     * @return 包含状态、响应头及原始字节的结果，包括 4xx/5xx
     */
    public EntityExchangeResult<byte[]> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        var request = client.method(method).uri(path).headers(target -> target.addAll(headers));
        if (body != null) {
            request.body(body);
        }
        return request.exchange().expectBody().returnResult();
    }
}
