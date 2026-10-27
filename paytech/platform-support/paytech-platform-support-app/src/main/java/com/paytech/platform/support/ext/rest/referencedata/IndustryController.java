package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.IndustryQueryApi;
import com.paytech.platform.support.api.referencedata.response.IndustryListResponseBody;
import com.paytech.platform.support.referencedata.service.processor.ListIndustriesProcessor;

import lombok.AllArgsConstructor;

/** 两级行业查询的协议适配；不开事务。 */
@RestController
@AllArgsConstructor
public class IndustryController implements IndustryQueryApi {

    private final ListIndustriesProcessor processor;

    private final ReferenceDataQueryConverter converter;

    /** 按可选层级、上级大类、状态与编码查询。 */
    @Override
    public IndustryListResponseBody listIndustries(Integer level, String parentCode, String status, String codes) {
        return converter.industryList(processor.process(converter.industries(level, parentCode, status, codes)));
    }
}
