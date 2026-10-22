package com.paytech.operations.console.bff.integration.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 数据查询合同与 BFF 页面模型之间的严格投影和回执核对
 * <li>页面模型与 Platform 合同字段一一对应；转换遇到多出字段或缺少基本类型字段即按上游无效响应 502 处理，暴露合同漂移。</li>
 * <li>分页窗口、当前页身份唯一、详情身份与写回执的对象类型、身份和状态必须与本次请求一致。</li>
 *
 * @author Claude
 */
final class DataQueryContractConverter {
    /** Platform 写回执中数据源的对象类型。 */
    static final String DATA_SOURCE = "DATA_SOURCE";

    /** Platform 写回执中查询模板的对象类型。 */
    static final String QUERY_TEMPLATE = "QUERY_TEMPLATE";

    private static final String ID = "[1-9][0-9]{0,18}";
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 50, 100, 200);
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final JsonMapper STRICT = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    /** 只承载转换，禁止创建实例。 */
    private DataQueryContractConverter() {
    }

    /** 严格字段对应的模型转换；值为空、缺少基本类型字段或多出字段时视为上游无效响应。 */
    static <T> T convert(Object value, Class<T> type) {
        require(value != null);
        try {
            return STRICT.convertValue(value, type);
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }

    /**
     * 分页结果核对与逐项投影：页码、条数与请求一致，当前页条数符合总数窗口，身份不重复；总数原样保留
     *
     * @param value Platform 分页结果
     * @param page 页面请求页码，缺省 1
     * @param size 页面请求条数，缺省 10
     * @param type 页面元素类型
     * @param identity 元素身份
     * @return 页面分页结果
     */
    static <S, T> PagingResponseBody<T> page(PagingResponseBody<S> value, Integer page, Integer size, Class<T> type,
        Function<T, String> identity) {
        int p = page == null ? DEFAULT_PAGE : page;
        int s = size == null ? DEFAULT_SIZE : size;
        require(value != null && value.items() != null && value.page() == p && value.size() == s && p > 0
            && PAGE_SIZES.contains(s) && value.total() >= 0);
        long offset = ((long) p - 1) * s;
        require(value.items().size() == Math.min((long) s, Math.max(0L, value.total() - offset)));
        var mapped = value.map(item -> convert(item, type));
        var identities = new HashSet<String>();
        require(mapped.items().stream().allMatch(item -> identities.add(identity.apply(item))));
        return mapped;
    }

    /** 返回对象身份必须与请求一致，否则 502，不把别的对象显示给用户。 */
    static void sameIdentity(String requested, String returned) {
        require(requested != null && requested.equals(returned));
    }

    /**
     * 核对写回执
     *
     * @param value Platform 回执
     * @param objectType 本次对象类型
     * @param objectId 本次对象 ID；新建时为 null，只校验格式
     * @param objectCode 本次对象编码；修改类操作为 null，不核对
     * @param expectedStatus 本次操作后应有的状态；无法从请求确定时为 null
     * @return 浏览器回执
     */
    static ConfigurationMutationResponseBody mutation(
        com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody value,
        String objectType, String objectId, String objectCode, String expectedStatus) {
        require(value != null && objectType.equals(value.getObjectType()) && value.getObjectId() != null
            && value.getObjectId().matches(ID) && (objectId == null || objectId.equals(value.getObjectId()))
            && (objectCode == null || objectCode.equals(value.getObjectCode()))
            && (expectedStatus == null || expectedStatus.equals(value.getStatus()))
            && value.getStatus() != null && value.getVersion() > 0);
        return new ConfigurationMutationResponseBody(value.getObjectType(), value.getObjectId(), value.getObjectCode(),
            value.getStatus(), value.getVersion());
    }

    /** 拒绝不符合已批准响应合同的 Owner 数据，保留 502 错误语义。 */
    static void require(boolean valid) {
        if (!valid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }
}
