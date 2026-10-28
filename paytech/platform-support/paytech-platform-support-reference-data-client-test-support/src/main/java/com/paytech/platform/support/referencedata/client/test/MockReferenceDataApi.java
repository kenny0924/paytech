package com.paytech.platform.support.referencedata.client.test;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
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
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;

/**
 * 在独立 Mock 服务实现 Platform 五个参考数据查询合同（{@code /platform/v1/reference-data/*}，无 Context Path），
 * 按 {@link ReferenceDataMocker} 的配置返回 DTO；MCC 按 page / size 真实分页。
 * <li>由消费方的 {@code @EnableMockHttpServer} 扫描本包装配，或在组件测试中显式传给 {@code new MockHttpServer(...)}。</li>
 */
@RestController
@TestComponent
public class MockReferenceDataApi implements ReferenceCurrencyQueryApi, ReferenceCountryQueryApi,
    CountrySubdivisionQueryApi, IndustryQueryApi, MccQueryApi {

    private final ReferenceDataMocker mocker;

    /**
     * 委托给 Mocker
     *
     * @param mocker 参考数据 Mocker
     */
    public MockReferenceDataApi(ReferenceDataMocker mocker) {
        this.mocker = mocker;
    }

    /** 全量币种。 */
    @Override
    public ReferenceCurrencyListResponseBody listCurrencies(String status, String codes) {
        observeRequest();
        ReferenceCurrencyListResponseBody body = new ReferenceCurrencyListResponseBody();
        body.setItems(mocker.answerList(ReferenceDataKind.CURRENCY, anyPresent(status, codes),
            ReferenceCurrencyResponseBody.class));
        return body;
    }

    /** 全量国家。 */
    @Override
    public ReferenceCountryListResponseBody listCountries(String status, String codes) {
        observeRequest();
        ReferenceCountryListResponseBody body = new ReferenceCountryListResponseBody();
        body.setItems(mocker.answerList(ReferenceDataKind.COUNTRY, anyPresent(status, codes),
            ReferenceCountryResponseBody.class));
        return body;
    }

    /** 全量行政区。 */
    @Override
    public CountrySubdivisionListResponseBody listSubdivisions(String countryCode, String status, String codes) {
        observeRequest();
        CountrySubdivisionListResponseBody body = new CountrySubdivisionListResponseBody();
        body.setItems(mocker.answerList(ReferenceDataKind.SUBDIVISION, anyPresent(countryCode, status, codes),
            CountrySubdivisionResponseBody.class));
        return body;
    }

    /** 全量行业。 */
    @Override
    public IndustryListResponseBody listIndustries(Integer level, String parentCode, String status, String codes) {
        observeRequest();
        IndustryListResponseBody body = new IndustryListResponseBody();
        body.setItems(mocker.answerList(ReferenceDataKind.INDUSTRY, level != null || anyPresent(parentCode, status, codes),
            IndustryResponseBody.class));
        return body;
    }

    /** 分页 MCC。 */
    @Override
    public PagingResponseBody<MccResponseBody> searchMccs(String keyword, String codes, String status, Integer page,
        Integer size) {
        observeRequest();
        return mocker.answerMccPage(anyPresent(keyword, codes, status), page, size);
    }

    /** 是否带了任一筛选条件。 */
    private static boolean anyPresent(String... filters) {
        for (String filter : filters) {
            if (filter != null) {
                return true;
            }
        }
        return false;
    }

    /** 记录 Authorization 与 X-Caller-Service 头，并给当前响应写入与 Problem 体一致的 Request ID（公共客户端据此校验故障响应）。 */
    private void observeRequest() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        mocker.recordIdentity(attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION),
            attributes.getRequest().getHeader("X-Caller-Service"));
        attributes.getResponse().setHeader(ApiProblemProfile.REQUEST_ID_HEADER, ReferenceDataMocker.REQUEST_ID);
    }
}
