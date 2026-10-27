package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.CountrySubdivisionQueryApi;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.referencedata.service.processor.ListCountrySubdivisionsProcessor;

import lombok.AllArgsConstructor;

/** 行政区查询的协议适配；不开事务。 */
@RestController
@AllArgsConstructor
public class CountrySubdivisionController implements CountrySubdivisionQueryApi {

    private final ReferenceDataQueryConverter converter;

    private final ListCountrySubdivisionsProcessor processor;

    /** 按可选国家、状态与编码查询。 */
    @Override
    public CountrySubdivisionListResponseBody listSubdivisions(String countryCode, String status, String codes) {
        return converter.subdivisionList(processor.process(converter.subdivisions(countryCode, status, codes)));
    }
}
