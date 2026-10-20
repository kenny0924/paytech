package com.paytech.operations.console.bff.configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * commercial 业务拒绝的 Owner detail 透传（F9，仅 /commercial-admin 使用的 commercial 代理）
 * <li>Foundation HttpServiceClientFactory 已按严格 Problem 合同校验 4xx 响应，并把原始 RestClientResponseException 作为
 * Cause 保留，但异常消息固定为公共默认说明（"远端 detail 不外显"）；页面因此只能看到通用 400。</li>
 * <li>本包装在 commercial 代理外层把 Cause 中已校验 Problem 的 detail 改写为同类型异常的消息，MVC 边界据此输出 Owner 的中文拒绝原因；
 * 错误码、HTTP 状态与 Cause 不变。commercial 的业务异常消息按项目规范是可安全外显的提示。</li>
 * <li>只处理精确的公共业务类别（BUSINESS_CHECK_FAILED、INVALID_REQUEST、CONCURRENT_REQUEST）与 FailException 本身（NOT_FOUND、
 * 已注册合同码）；字段校验异常、5xx、超时与协议错误保持 Foundation 原样。Problem 不可读、detail 为空或超长时保留原消息。</li>
 *
 * @author Claude
 */
final class CommercialOwnerProblemDetail {
    /** 外显 detail 的长度上限，超长视为异常响应而保留公共说明。 */
    static final int MAX_DETAIL_LENGTH = 1000;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CommercialOwnerProblemDetail() {
    }

    /**
     * 为 commercial 接口代理加上 Owner detail 透传
     *
     * @param type 接口类型
     * @param client Foundation 创建的接口代理
     * @return 同接口的包装代理
     */
    static <T> T wrap(Class<T> type, T client) {
        InvocationHandler handler = (proxy, method, args) -> invoke(client, method, args);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object invoke(Object client, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(client, args);
        } catch (InvocationTargetException failure) {
            throw project(failure.getCause());
        }
    }

    /** 已知业务类别改写为携带 Owner detail 的同类型异常；其他异常原样返回。 */
    static Throwable project(Throwable failure) {
        if (!(failure instanceof FailException fail) || !(fail.getCause() instanceof RestClientResponseException response)) {
            return failure;
        }
        String detail = ownerDetail(response);
        if (detail == null) {
            return failure;
        }
        Class<?> type = failure.getClass();
        if (type == BizCheckFailException.class) {
            return new BizCheckFailException(detail, response);
        }
        if (type == InvalidParameterException.class) {
            return new InvalidParameterException(detail, response);
        }
        if (type == ConcurrentRequestException.class) {
            return new ConcurrentRequestException(detail, response);
        }
        if (type == FailException.class) {
            return new FailException(fail.getErrorCode(), detail, response);
        }
        return failure;
    }

    /** 已校验 Problem 的 detail；非 Problem、不可读、空白或超长返回 null。 */
    static String ownerDetail(RestClientResponseException response) {
        if (!response.getStatusCode().is4xxClientError() || !isProblem(response.getResponseHeaders())) {
            return null;
        }
        try {
            JsonNode detail = MAPPER.readTree(response.getResponseBodyAsString()).path("detail");
            if (!detail.isString()) {
                return null;
            }
            String value = detail.stringValue().strip();
            return value.isEmpty() || value.length() > MAX_DETAIL_LENGTH ? null : value;
        } catch (JacksonException unreadable) {
            return null;
        }
    }

    private static boolean isProblem(HttpHeaders headers) {
        List<String> contentTypes = headers == null ? null : headers.get(HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null || contentTypes.size() != 1) {
            return false;
        }
        try {
            return MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(MediaType.parseMediaType(contentTypes.getFirst()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
