package com.paytech.platform.support.referencedata.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCountry;
import com.paytech.platform.support.referencedata.domainservice.repository.ReferenceCountryRepository;
import com.paytech.platform.support.referencedata.service.processor.request.ListReferenceCountriesRequest;
import com.paytech.platform.support.referencedata.service.processor.response.ReferenceCountryResult;

import lombok.AllArgsConstructor;

/**
 * 查询参考数据国家并批量换取国旗公开地址；只读，不开事务
 * <li>旧接口 /platform/v1/country-regions 也经本 Processor 读取全部国家，只投影国家与区域两列。</li>
 */
@Component
@AllArgsConstructor
public class ListReferenceCountriesProcessor
    extends AbstractProcessor<ListReferenceCountriesRequest, List<ReferenceCountryResult>> {

    private final ReferenceDataLogos logos;

    private final ReferenceCountryRepository repository;

    @Override
    protected List<ReferenceCountryResult> processRequest(ListReferenceCountriesRequest request) {
        List<ReferenceCountry> rows = repository.find(request.getStatus(), request.getCodes());
        Map<String, String> urls = logos.publicUrls(rows.stream().map(ReferenceCountry::getLogoFileAddress).toList());
        return rows.stream().map(row -> ReferenceCountryResult.of(row, urls.get(row.getLogoFileAddress()))).toList();
    }
}
