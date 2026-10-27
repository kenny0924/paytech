package com.paytech.client.master.test.mock.external.platform;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.test.mocker.platform.ReferenceDataMocker;
import com.paytech.client.master.test.mocker.platform.ReferenceDataMocker.Catalog;
import com.paytech.client.master.test.mocker.platform.ReferenceDataMocker.Entry;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;

import lombok.AllArgsConstructor;

/**
 * Platform 参考数据查询的测试 HTTP 实现：按 codes 返回 ReferenceDataMocker 配置的条目
 * <li>client-master 只按编码查询；带 status、countryCode、level、parentCode、keyword 等其他筛选条件，或目录未配置，均为意外调用。</li>
 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockPlatformReferenceDataApi implements ReferenceCountryQueryApi, ReferenceCurrencyQueryApi, CountrySubdivisionQueryApi,
    IndustryQueryApi, MccQueryApi {
    private final ReferenceDataMocker mocker;

    /** 国家。 */
    @Override
    public ReferenceCountryListResponseBody listCountries(String status, String codes) {
        requireOnlyCodes(status);
        var body = new ReferenceCountryListResponseBody();
        body.setItems(entries(Catalog.COUNTRY, codes).stream().map(entry -> {
            var item = new ReferenceCountryResponseBody();
            item.setCountryCode(entry.code());
            item.setName("Country " + entry.code());
            item.setStatus(entry.status());
            item.setVersion(1);
            return item;
        }).toList());
        return body;
    }

    /** 币种。 */
    @Override
    public ReferenceCurrencyListResponseBody listCurrencies(String status, String codes) {
        requireOnlyCodes(status);
        var body = new ReferenceCurrencyListResponseBody();
        body.setItems(entries(Catalog.CURRENCY, codes).stream().map(entry -> {
            var item = new ReferenceCurrencyResponseBody();
            item.setCurrencyCode(entry.code());
            item.setName("Currency " + entry.code());
            item.setMinorUnit(2);
            item.setRoundingMode("HALF_UP");
            item.setStatus(entry.status());
            item.setVersion(1);
            return item;
        }).toList());
        return body;
    }

    /** 行政区。 */
    @Override
    public CountrySubdivisionListResponseBody listSubdivisions(String countryCode, String status, String codes) {
        requireOnlyCodes(countryCode, status);
        var body = new CountrySubdivisionListResponseBody();
        body.setItems(entries(Catalog.SUBDIVISION, codes).stream().map(entry -> {
            var item = new CountrySubdivisionResponseBody();
            item.setSubdivisionCode(entry.code());
            item.setCountryCode(entry.countryCode());
            item.setCategory("EMIRATE");
            item.setName("Subdivision " + entry.code());
            item.setStatus(entry.status());
            item.setVersion(1);
            return item;
        }).toList());
        return body;
    }

    /** 行业。 */
    @Override
    public IndustryListResponseBody listIndustries(Integer level, String parentCode, String status, String codes) {
        requireOnlyCodes(level, parentCode, status);
        var body = new IndustryListResponseBody();
        body.setItems(entries(Catalog.INDUSTRY, codes).stream().map(entry -> {
            var item = new IndustryResponseBody();
            item.setIndustryCode(entry.code());
            item.setLevel(entry.level());
            item.setName("Industry " + entry.code());
            item.setSortNo(10);
            item.setStatus(entry.status());
            item.setVersion(1);
            return item;
        }).toList());
        return body;
    }

    /** MCC：client-master 固定取第 1 页、每页 50 条。 */
    @Override
    public PagingResponseBody<MccResponseBody> searchMccs(String keyword, String codes, String status, Integer page, Integer size) {
        requireOnlyCodes(keyword, status);
        if (!Integer.valueOf(1).equals(page) || !Integer.valueOf(50).equals(size)) {
            throw unexpected();
        }
        List<MccResponseBody> items = entries(Catalog.MCC, codes).stream().map(entry -> {
            var item = new MccResponseBody();
            item.setMccCode(entry.code());
            item.setName("MCC " + entry.code());
            item.setNetworkHighRisk(false);
            item.setStatus(entry.status());
            item.setVersion(1);
            return item;
        }).toList();
        return new PagingResponseBody<>(items, 1, 50, items.size());
    }

    /** 读取配置；故障按约定状态返回，未配置为意外调用。 */
    @SuppressWarnings("unchecked")
    List<Entry> entries(Catalog catalog, String codes) {
        requestId();
        Object reply = mocker.lookup(catalog, codes);
        if (reply instanceof ReferenceDataMocker.Fault fault) {
            throw MockHttpFault.json(fault.status(), fault.problem());
        }
        if (reply == null) {
            throw unexpected();
        }
        return (List<Entry>)reply;
    }

    /** client-master 只按 codes 查询。 */
    static void requireOnlyCodes(Object... otherFilters) {
        for (Object filter : otherFilters) {
            if (filter != null) {
                throw unexpected();
            }
        }
    }

    /** 意外调用。 */
    static IllegalStateException unexpected() {
        return new IllegalStateException("未配置的 Platform 参考数据调用");
    }

    /** 响应 Request ID，满足严格 Problem 合同。 */
    static void requestId() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", "cct-onboarding-test");
    }
}
