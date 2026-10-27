package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformReferenceDataClient;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountryListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.CountrySubdivisionListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.IndustryListResponseBody;
import com.paytech.operations.console.bff.service.model.ReferenceDataModels.MccResponseBody;
import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;

import lombok.AllArgsConstructor;

/**
 * Platform 参考数据查询的 HTTP 转发：不带操作人，不重试，结果经 {@link ReferenceDataContractConverter} 核对
 * <li>按编码读取 MCC 时每批最多 50 个编码（Platform 合同上限），按编码升序合并。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformReferenceDataClientImpl implements PlatformReferenceDataClient {
    /** Platform 单次 codes 上限，同时作为该批的每页条数。 */
    static final int CODES_PER_CALL = 50;

    private final ReferenceCountryQueryApi countryApi;
    private final CountrySubdivisionQueryApi subdivisionApi;
    private final IndustryQueryApi industryApi;
    private final MccQueryApi mccApi;

    @Override
    public CountryListResponseBody countries(String status) {
        var response = countryApi.listCountries(status, null);
        require(response != null);
        var result = new CountryListResponseBody();
        result.setItems(ReferenceDataContractConverter.countries(response.getItems()));
        return result;
    }

    @Override
    public CountrySubdivisionListResponseBody subdivisions(String countryCode, String status) {
        var response = subdivisionApi.listSubdivisions(countryCode, status, null);
        require(response != null);
        var items = ReferenceDataContractConverter.subdivisions(response.getItems());
        require(countryCode == null || items.stream().allMatch(item -> countryCode.equals(item.getCountryCode())));
        var result = new CountrySubdivisionListResponseBody();
        result.setItems(items);
        return result;
    }

    @Override
    public IndustryListResponseBody industries(String status) {
        var response = industryApi.listIndustries(null, null, status, null);
        require(response != null);
        var result = new IndustryListResponseBody();
        result.setItems(ReferenceDataContractConverter.industries(response.getItems()));
        return result;
    }

    @Override
    public PagingResponseBody<MccResponseBody> searchMccs(String keyword, String status, int page, int size) {
        return ReferenceDataContractConverter.mccs(mccApi.searchMccs(keyword, null, status, page, size), page, size);
    }

    @Override
    public List<MccResponseBody> mccsByCodes(Collection<String> codes) {
        List<String> distinct = codes.stream().distinct().sorted().toList();
        List<MccResponseBody> result = new ArrayList<>();
        for (int from = 0; from < distinct.size(); from += CODES_PER_CALL) {
            var batch = distinct.subList(from, Math.min(distinct.size(), from + CODES_PER_CALL));
            var page = ReferenceDataContractConverter.mccs(
                mccApi.searchMccs(null, String.join(",", batch), null, 1, CODES_PER_CALL), 1, CODES_PER_CALL);
            require(page.items().stream().allMatch(item -> batch.contains(item.getMccCode())));
            result.addAll(page.items());
        }
        result.sort(Comparator.comparing(MccResponseBody::getMccCode));
        return result;
    }
}
