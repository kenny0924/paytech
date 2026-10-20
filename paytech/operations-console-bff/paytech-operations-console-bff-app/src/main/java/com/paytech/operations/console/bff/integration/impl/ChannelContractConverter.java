package com.paytech.operations.console.bff.integration.impl;

import java.util.Map;
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
 * channel-connector 合同与 BFF 渠道页面模型之间的严格转换与回执核对
 * <li>页面模型与 Owner DTO 字段一一对应：Owner 多出字段或缺少基本类型字段即视为合同漂移，按上游无效响应 502 处理。</li>
 * <li>命令回执的业务身份必须与请求一致；Owner 422 的字段路径只投影到本次页面请求体内存在的路径（BFF 补入的操作人字段不外显）。</li>
 * <li>分页回执的 page / size 必须与请求一致，total 非负、条目不超页且无 null。</li>
 *
 * @author Claude
 */
final class ChannelContractConverter {
    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    /** 只承载静态方法，禁止创建实例。 */
    private ChannelContractConverter() {
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
            throw new IllegalStateException("页面模型与 channel-connector 请求合同不一致: " + type.getSimpleName(), invalid);
        }
    }

    /**
     * 命令转换：Owner 422 的字段路径只投影到本次页面请求体内存在的路径
     *
     * @param request 页面请求体；无 Body 的命令为 null，此时 Owner 字段路径全部丢弃
     * @param call 一次 Owner 调用
     * @param type 页面响应模型
     * @return 严格转换后的回执
     */
    static <T> T write(Object request, Supplier<Object> call, Class<T> type) {
        try {
            return convert(call.get(), type);
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request == null ? Map.of() : request, failure);
        }
    }

    /** 分页回执核对后逐条严格转换，保留 page / size / total。 */
    static <T> PagingResponseBody<T> page(PagingResponseBody<?> owner, int page, int size, Class<T> type) {
        require(owner != null && owner.page() == page && owner.size() == size && owner.total() >= 0
            && owner.items().size() <= size && owner.items().stream().allMatch(Objects::nonNull));
        return owner.map(item -> convert(item, type));
    }

    /** 201 / 200 回执取 Body。 */
    static Object body(org.springframework.http.ResponseEntity<?> response) {
        return response == null ? null : response.getBody();
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
