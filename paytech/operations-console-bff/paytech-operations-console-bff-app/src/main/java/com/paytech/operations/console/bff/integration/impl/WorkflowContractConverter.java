package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.function.Supplier;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 工作流页面模型与 Platform 合同之间的转换与回执核对
 * <li>请求 record 与 Platform 请求类逐字段对应，转换遇到未知字段即失败（合同漂移）。</li>
 * <li>写回执的对象类型与路径中的对象 ID 必须与本次操作一致，否则按上游无效响应 502。</li>
 * <li>Platform 422 的字段路径只投影到本次页面请求体内存在的路径；完成动作与发起实例另见 {@link WorkflowRuntimeValidationConverter}。</li>
 */
final class WorkflowContractConverter {

    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private WorkflowContractConverter() {
    }

    /** 页面请求转为 Platform 请求类。 */
    static <T> T toPlatform(Object request, Class<T> type) {
        try {
            return STRICT.convertValue(request, type);
        } catch (JacksonException | IllegalArgumentException drift) {
            throw new ErrorException(CommonErrorCode.INTERNAL_ERROR, null, drift);
        }
    }

    /** 写入转发：422 字段路径投影到本次请求体。 */
    static <T> T write(Object request, Supplier<T> call) {
        try {
            return require(call.get());
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /**
     * 运行时写入转发（完成动作、发起实例）：422 中提交值根下的字段路径即使不在请求体内也保留，登记的 Platform 固定文案透传。
     *
     * @param request 页面请求体
     * @param root 提交值属性名（output / startValues）
     * @param call Platform 调用
     * @return Platform 响应
     */
    static <T> T runtimeWrite(Object request, String root, Supplier<T> call) {
        try {
            return require(call.get());
        } catch (HttpRequestValidationException failure) {
            throw WorkflowRuntimeValidationConverter.local(request, root, failure);
        }
    }

    /** 回执的对象类型须为 expectedType；expectedId 非空时对象 ID 也须一致。 */
    static WorkflowMutationResponseBody mutation(WorkflowMutationResponseBody value, String expectedType,
        String expectedId) {
        require(value);
        if (!expectedType.equals(value.getObjectType()) || value.getObjectId() == null
            || expectedId != null && !Objects.equals(expectedId, value.getObjectId())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return value;
    }

    /** 上游响应不能为空。 */
    static <T> T require(T value) {
        if (value == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return value;
    }
}
