package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * ledger 与 account 合同到 BFF 页面模型的严格转换与回执核对
 * <li>本地模型与 Owner DTO 字段一一对应：Owner 多出字段或缺少基本类型字段即视为合同漂移，按上游无效响应 502 处理。</li>
 * <li>分页回执的页码、条数、总数与当前页条目数必须自洽；命令回执的主键必须是正十进制整数字符串。</li>
 *
 * @author Claude
 */
final class FinanceContractConverter {
    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private static final String ID = "[1-9][0-9]{0,18}";

    private FinanceContractConverter() {
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

    /** 分页回执与请求窗口一致、条目数与总数自洽后逐项转换；不改上游总数。 */
    static <S, T> PagingResponseBody<T> page(PagingResponseBody<S> value, int page, int size, Class<T> type) {
        require(value != null && value.items() != null && value.page() == page && value.size() == size
            && value.total() >= 0);
        long remaining = Math.max(0L, value.total() - ((long) page - 1) * size);
        require(value.items().size() == Math.min((long) size, remaining));
        return value.map(item -> convert(item, type));
    }

    /** 命令转换：Owner 422 的字段路径只投影到本次页面请求体内存在的路径。 */
    static <T> T write(Object request, Supplier<Object> call, Class<T> type) {
        try {
            return convert(call.get(), type);
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 新建回执必须带正十进制主键。 */
    static void requireIdentity(String value) {
        require(value != null && value.matches(ID));
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
