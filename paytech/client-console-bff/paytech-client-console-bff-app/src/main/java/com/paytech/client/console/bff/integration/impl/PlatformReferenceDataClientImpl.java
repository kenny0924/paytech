package com.paytech.client.console.bff.integration.impl;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;

import lombok.AllArgsConstructor;

/** 每类参考数据只按 status=ACTIVE 查询一次，不重试，并核对回执条目完整、状态与请求一致。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class PlatformReferenceDataClientImpl implements PlatformReferenceDataClient {
    private static final String ACTIVE = ReferenceDataStatus.ACTIVE.getCode();

    private final ReferenceCountryQueryApi countries;
    private final ReferenceCurrencyQueryApi currencies;
    private final CountrySubdivisionQueryApi subdivisions;

    /** 读取 ACTIVE 国家并核对编码、名称与状态。 */
    @Override
    public List<ReferenceCountryResponseBody> activeCountries() {
        var value = countries.listCountries(ACTIVE, null);
        return requireItems(value == null ? null : value.getItems(), ReferenceCountryResponseBody::getCountryCode,
            ReferenceCountryResponseBody::getName, ReferenceCountryResponseBody::getStatus, item -> true);
    }

    /** 读取 ACTIVE 币种并核对编码、名称与状态。 */
    @Override
    public List<ReferenceCurrencyResponseBody> activeCurrencies() {
        var value = currencies.listCurrencies(ACTIVE, null);
        return requireItems(value == null ? null : value.getItems(), ReferenceCurrencyResponseBody::getCurrencyCode,
            ReferenceCurrencyResponseBody::getName, ReferenceCurrencyResponseBody::getStatus, item -> true);
    }

    /** 读取 ACTIVE 行政区；指定国家时每条都必须属于该国。 */
    @Override
    public List<CountrySubdivisionResponseBody> activeSubdivisions(String countryCode) {
        var value = subdivisions.listSubdivisions(countryCode, ACTIVE, null);
        return requireItems(value == null ? null : value.getItems(), CountrySubdivisionResponseBody::getSubdivisionCode,
            CountrySubdivisionResponseBody::getName, CountrySubdivisionResponseBody::getStatus,
            item -> item.getCountryCode() != null && (countryCode == null || countryCode.equals(item.getCountryCode())));
    }

    /** 列表与每个条目都必须完整且为 ACTIVE；否则整个回执视为无效，不挑选部分条目。 */
    static <T> List<T> requireItems(List<T> items, Function<T, String> code, Function<T, String> name,
        Function<T, String> status, Predicate<T> scoped) {
        if (items == null || items.stream().anyMatch(item -> item == null || isBlank(code.apply(item))
            || isBlank(name.apply(item)) || !Objects.equals(ACTIVE, status.apply(item)) || !scoped.test(item))) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return items;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
