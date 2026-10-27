package com.paytech.platform.support.ext.rest.referencedata;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.IndustryResponseBody;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;
import com.paytech.platform.support.referencedata.service.processor.request.ListCountrySubdivisionsRequest;
import com.paytech.platform.support.referencedata.service.processor.request.ListIndustriesRequest;
import com.paytech.platform.support.referencedata.service.processor.request.ListReferenceCountriesRequest;
import com.paytech.platform.support.referencedata.service.processor.request.ListReferenceCurrenciesRequest;
import com.paytech.platform.support.referencedata.service.processor.request.SearchMccsRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CountrySubdivisionResult;
import com.paytech.platform.support.referencedata.service.processor.response.IndustryResult;
import com.paytech.platform.support.referencedata.service.processor.response.MccResult;
import com.paytech.platform.support.referencedata.service.processor.response.ReferenceCountryResult;
import com.paytech.platform.support.referencedata.service.processor.response.ReferenceCurrencyResult;

/**
 * 参考数据查询的协议转换
 * <li>Query 参数解析：编码列表按逗号拆分、去除空白并转大写，状态按稳定 Code 解析；格式、数量与取值范围由内部请求注解在 Processor
 * 中校验，失败均返回 400。</li>
 * <li>枚举按 Code 输出；不返回本地化名称。</li>
 */
@Component
public class ReferenceDataQueryConverter {

    /** 币种查询条件。 */
    public ListReferenceCurrenciesRequest currencies(String status, String codes) {
        return new ListReferenceCurrenciesRequest(status(status), codes(codes));
    }

    /** 国家查询条件。 */
    public ListReferenceCountriesRequest countries(String status, String codes) {
        return new ListReferenceCountriesRequest(status(status), codes(codes));
    }

    /** 行政区查询条件。 */
    public ListCountrySubdivisionsRequest subdivisions(String countryCode, String status, String codes) {
        return new ListCountrySubdivisionsRequest(code(countryCode), status(status), codes(codes));
    }

    /** 行业查询条件。 */
    public ListIndustriesRequest industries(Integer level, String parentCode, String status, String codes) {
        return new ListIndustriesRequest(level, code(parentCode), status(status), codes(codes));
    }

    /** MCC 搜索条件；页码与条数按公共分页规则校验。 */
    public SearchMccsRequest mccs(String keyword, String codes, String status, Integer page, Integer size) {
        String text = keyword == null || keyword.isBlank() ? null : keyword.strip();
        return new SearchMccsRequest(text, status(status), codes(codes), PagingRequestBody.of(page, size));
    }

    /** 币种结果，保持 Owner 的升序。 */
    public ReferenceCurrencyListResponseBody currencyList(List<ReferenceCurrencyResult> results) {
        ReferenceCurrencyListResponseBody body = new ReferenceCurrencyListResponseBody();
        body.setItems(results.stream().map(ReferenceDataQueryConverter::currency).toList());
        return body;
    }

    /** 国家结果，保持 Owner 的升序。 */
    public ReferenceCountryListResponseBody countryList(List<ReferenceCountryResult> results) {
        ReferenceCountryListResponseBody body = new ReferenceCountryListResponseBody();
        body.setItems(results.stream().map(ReferenceDataQueryConverter::country).toList());
        return body;
    }

    /** 行政区结果，保持 Owner 的升序。 */
    public CountrySubdivisionListResponseBody subdivisionList(List<CountrySubdivisionResult> results) {
        CountrySubdivisionListResponseBody body = new CountrySubdivisionListResponseBody();
        body.setItems(results.stream().map(ReferenceDataQueryConverter::subdivision).toList());
        return body;
    }

    /** 行业结果，保持 Owner 的大类、细类顺序。 */
    public IndustryListResponseBody industryList(List<IndustryResult> results) {
        IndustryListResponseBody body = new IndustryListResponseBody();
        body.setItems(results.stream().map(ReferenceDataQueryConverter::industry).toList());
        return body;
    }

    /** MCC 分页结果，保留页码、条数与总数。 */
    public PagingResponseBody<MccResponseBody> mccPage(PagingResponseBody<MccResult> results) {
        return results.map(ReferenceDataQueryConverter::mcc);
    }

    /** 可选状态 Code；为空不限制，未知 Code 返回 400。 */
    static ReferenceDataStatus status(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        ReferenceDataStatus parsed = EnumUtils.getEnumByCode(ReferenceDataStatus.class, status.strip());
        if (parsed == null) {
            throw new InvalidParameterException("status 只能是 DRAFT、ACTIVE、INACTIVE: " + status);
        }
        return parsed;
    }

    /** 逗号分隔的编码列表；为空不限制，空白项保留为空串交给格式校验拒绝。 */
    static List<String> codes(String codes) {
        if (codes == null || codes.isBlank()) {
            return null;
        }
        return Arrays.stream(codes.split(",", -1)).map(code -> code.strip().toUpperCase(Locale.ROOT)).toList();
    }

    /** 单个编码去除空白并转大写；为空返回空。 */
    static String code(String code) {
        return code == null || code.isBlank() ? null : code.strip().toUpperCase(Locale.ROOT);
    }

    /** 币种响应元素。 */
    static ReferenceCurrencyResponseBody currency(ReferenceCurrencyResult result) {
        ReferenceCurrencyResponseBody body = new ReferenceCurrencyResponseBody();
        body.setCurrencyCode(result.getCurrencyCode());
        body.setNumericCode(result.getNumericCode());
        body.setName(result.getName());
        body.setSymbol(result.getSymbol());
        body.setMinorUnit(result.getMinorUnit());
        body.setRoundingMode(result.getRoundingMode().getCode());
        body.setLogoFileAddress(result.getLogoFileAddress());
        body.setLogoPublicUrl(result.getLogoPublicUrl());
        body.setStatus(result.getStatus().getCode());
        body.setVersion(result.getVersion());
        return body;
    }

    /** 国家响应元素。 */
    static ReferenceCountryResponseBody country(ReferenceCountryResult result) {
        ReferenceCountryResponseBody body = new ReferenceCountryResponseBody();
        body.setCountryCode(result.getCountryCode());
        body.setAlpha3Code(result.getAlpha3Code());
        body.setNumericCode(result.getNumericCode());
        body.setName(result.getName());
        body.setRegionCode(result.getRegionCode());
        body.setCallingCode(result.getCallingCode());
        body.setDefaultCurrencyCode(result.getDefaultCurrencyCode());
        body.setLogoFileAddress(result.getLogoFileAddress());
        body.setLogoPublicUrl(result.getLogoPublicUrl());
        body.setStatus(result.getStatus().getCode());
        body.setVersion(result.getVersion());
        return body;
    }

    /** 行政区响应元素。 */
    static CountrySubdivisionResponseBody subdivision(CountrySubdivisionResult result) {
        CountrySubdivisionResponseBody body = new CountrySubdivisionResponseBody();
        body.setSubdivisionCode(result.getSubdivisionCode());
        body.setCountryCode(result.getCountryCode());
        body.setParentSubdivisionCode(result.getParentSubdivisionCode());
        body.setCategory(result.getCategory());
        body.setName(result.getName());
        body.setLocalName(result.getLocalName());
        body.setStatus(result.getStatus().getCode());
        body.setVersion(result.getVersion());
        return body;
    }

    /** 行业响应元素。 */
    static IndustryResponseBody industry(IndustryResult result) {
        IndustryResponseBody body = new IndustryResponseBody();
        body.setIndustryCode(result.getIndustryCode());
        body.setParentIndustryCode(result.getParentIndustryCode());
        body.setLevel(result.getLevel());
        body.setName(result.getName());
        body.setDescription(result.getDescription());
        body.setDefaultMccCode(result.getDefaultMccCode());
        body.setLogoFileAddress(result.getLogoFileAddress());
        body.setLogoPublicUrl(result.getLogoPublicUrl());
        body.setSortNo(result.getSortNo());
        body.setStatus(result.getStatus().getCode());
        body.setVersion(result.getVersion());
        return body;
    }

    /** MCC 响应元素。 */
    static MccResponseBody mcc(MccResult result) {
        MccResponseBody body = new MccResponseBody();
        body.setMccCode(result.getMccCode());
        body.setName(result.getName());
        body.setDescription(result.getDescription());
        body.setNetworkHighRisk(result.isNetworkHighRisk());
        body.setStatus(result.getStatus().getCode());
        body.setVersion(result.getVersion());
        return body;
    }
}
