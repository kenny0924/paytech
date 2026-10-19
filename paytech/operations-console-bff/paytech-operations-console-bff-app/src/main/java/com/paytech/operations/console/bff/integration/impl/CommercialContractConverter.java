package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * commercial 合同与 BFF 页面模型之间的严格转换与回执核对
 * <li>页面模型与 Owner DTO 字段一一对应：Owner 多出字段或缺少基本类型字段即视为合同漂移，按上游无效响应 502 处理。</li>
 * <li>命令回执的业务身份必须与请求一致；Owner 422 的字段路径只投影到本次页面请求体内存在的路径。</li>
 *
 * @author Claude
 */
final class CommercialContractConverter {
    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private CommercialContractConverter() {
    }

    /** 严格字段对应的模型转换；空值或字段不对应时视为上游无效响应。 */
    static <T> T convert(Object value, Class<T> type) {
        require(value != null);
        try {
            return STRICT.convertValue(value, type);
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }

    /** 页面请求转 Owner 请求：同名字段一一对应，本地模型不得有 Owner 没有的字段。 */
    static <T> T request(Object request, Class<T> type) {
        try {
            return STRICT.convertValue(request, type);
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new IllegalStateException("页面模型与 commercial 请求合同不一致: " + type.getSimpleName(), invalid);
        }
    }

    /** 命令转换：Owner 422 的字段路径只投影到本次页面请求体内存在的路径。 */
    static <T> T write(Object request, Supplier<Object> call, Class<T> type) {
        try {
            return convert(call.get(), type);
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 回执中的业务身份必须与请求一致。 */
    static void requireSame(String expected, String actual) {
        require(Objects.equals(expected, actual));
    }

    /** 回执不符合合同时按上游无效响应处理。 */
    static void require(boolean valid) {
        if (!valid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }
}
