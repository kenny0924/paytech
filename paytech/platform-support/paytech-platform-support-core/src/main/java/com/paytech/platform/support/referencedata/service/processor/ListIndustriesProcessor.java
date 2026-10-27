package com.paytech.platform.support.referencedata.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Industry;
import com.paytech.platform.support.referencedata.domainservice.repository.IndustryRepository;
import com.paytech.platform.support.referencedata.service.processor.request.ListIndustriesRequest;
import com.paytech.platform.support.referencedata.service.processor.response.IndustryResult;

import lombok.AllArgsConstructor;

/** 查询两级行业并批量换取图标公开地址；只读，不开事务。 */
@Component
@AllArgsConstructor
public class ListIndustriesProcessor extends AbstractProcessor<ListIndustriesRequest, List<IndustryResult>> {

    private final ReferenceDataLogos logos;

    private final IndustryRepository repository;

    @Override
    protected List<IndustryResult> processRequest(ListIndustriesRequest request) {
        List<Industry> rows = repository.find(request.getLevel(), request.getParentCode(), request.getStatus(),
            request.getCodes());
        Map<String, String> urls = logos.publicUrls(rows.stream().map(Industry::getLogoFileAddress).toList());
        return rows.stream().map(row -> IndustryResult.of(row, urls.get(row.getLogoFileAddress()))).toList();
    }
}
