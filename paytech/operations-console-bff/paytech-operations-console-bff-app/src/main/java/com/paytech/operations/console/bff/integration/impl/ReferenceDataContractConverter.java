package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountryResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountrySubdivisionResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 参考数据合同到 BFF 页面模型的投影与核对
 * <li>同名字段投影；Platform 新增字段不投影（读取为宽松模式，便于 Platform 先行演进）。</li>
 * <li>编码格式、状态与层级任一不符合合同，或列表编码重复，按上游无效响应 502 处理；未知状态不放行。</li>
 *
 * @author Claude
 */
final class ReferenceDataContractConverter {
    /** 只投影本地模型声明的字段。 */
    private static final JsonMapper LENIENT = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final String COUNTRY = "[A-Z]{2}";
    private static final String SUBDIVISION = "[A-Z]{2}-[A-Z0-9]{1,3}";
    private static final String INDUSTRY = "[A-Z][A-Z0-9_]{0,63}";
    private static final String MCC = "[0-9]{4}";

    /** 只承载转换，禁止创建实例。 */
    private ReferenceDataContractConverter() {
    }

    /** 国家：代码与状态必须有效，代码不得重复。 */
    static List<CountryResponseBody> countries(List<?> items) {
        var result = list(items, CountryResponseBody.class);
        result.forEach(item -> require(matches(item.getCountryCode(), COUNTRY) && known(item.getStatus())));
        requireUnique(result, CountryResponseBody::getCountryCode);
        return result;
    }

    /** 行政区：代码、所属国家与状态必须有效，代码前两位即所属国家。 */
    static List<CountrySubdivisionResponseBody> subdivisions(List<?> items) {
        var result = list(items, CountrySubdivisionResponseBody.class);
        result.forEach(item -> require(matches(item.getSubdivisionCode(), SUBDIVISION)
            && matches(item.getCountryCode(), COUNTRY) && item.getSubdivisionCode().startsWith(item.getCountryCode() + "-")
            && known(item.getStatus())));
        requireUnique(result, CountrySubdivisionResponseBody::getSubdivisionCode);
        return result;
    }

    /** 行业：大类没有上级，细类必须有上级；建议 MCC 为四位数字或空。 */
    static List<IndustryResponseBody> industries(List<?> items) {
        var result = list(items, IndustryResponseBody.class);
        result.forEach(item -> require(matches(item.getIndustryCode(), INDUSTRY) && known(item.getStatus())
            && (Integer.valueOf(1).equals(item.getLevel()) && item.getParentIndustryCode() == null
                || Integer.valueOf(2).equals(item.getLevel()) && matches(item.getParentIndustryCode(), INDUSTRY))
            && (item.getDefaultMccCode() == null || matches(item.getDefaultMccCode(), MCC))));
        requireUnique(result, IndustryResponseBody::getIndustryCode);
        return result;
    }

    /** MCC 分页：窗口与请求一致、条目数与总数自洽后逐项核对。 */
    static <S> PagingResponseBody<MccResponseBody> mccs(PagingResponseBody<S> value, int page, int size) {
        require(value != null && value.items() != null && value.page() == page && value.size() == size && value.total() >= 0);
        long remaining = Math.max(0L, value.total() - ((long) page - 1) * size);
        require(value.items().size() == Math.min((long) size, remaining));
        return new PagingResponseBody<>(mccs(value.items()), value.page(), value.size(), value.total());
    }

    /** MCC 列表：编码与状态必须有效。 */
    static List<MccResponseBody> mccs(List<?> items) {
        var result = list(items, MccResponseBody.class);
        result.forEach(item -> require(matches(item.getMccCode(), MCC) && known(item.getStatus())));
        requireUnique(result, MccResponseBody::getMccCode);
        return result;
    }

    /** 逐项投影；列表缺失或元素为空视为上游无效响应。 */
    private static <T> List<T> list(List<?> items, Class<T> type) {
        require(items != null && items.stream().allMatch(java.util.Objects::nonNull));
        try {
            return items.stream().map(item -> LENIENT.convertValue(item, type)).toList();
        } catch (JacksonException | IllegalArgumentException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }

    private static <T> void requireUnique(List<T> items, Function<T, String> code) {
        require(new HashSet<>(items.stream().map(code).toList()).size() == items.size());
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private static boolean known(String status) {
        return status != null && Arrays.stream(ReferenceDataStatus.values()).anyMatch(value -> value.getCode().equals(status));
    }
}
