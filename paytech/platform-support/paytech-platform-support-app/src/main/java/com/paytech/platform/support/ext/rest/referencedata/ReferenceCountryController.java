package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.ReferenceCountryQueryApi;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.referencedata.service.processor.ListReferenceCountriesProcessor;

import lombok.AllArgsConstructor;

/** 参考数据国家查询的协议适配；不开事务。 */
@RestController
@AllArgsConstructor
public class ReferenceCountryController implements ReferenceCountryQueryApi {

    private final ReferenceDataQueryConverter converter;

    private final ListReferenceCountriesProcessor processor;

    /** 按可选状态与编码查询。 */
    @Override
    public ReferenceCountryListResponseBody listCountries(String status, String codes) {
        return converter.countryList(processor.process(converter.countries(status, codes)));
    }
}
