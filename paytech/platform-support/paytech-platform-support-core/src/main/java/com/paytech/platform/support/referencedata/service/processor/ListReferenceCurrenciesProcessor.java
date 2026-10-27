package com.paytech.platform.support.referencedata.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domainservice.repository.ReferenceCurrencyRepository;
import com.paytech.platform.support.referencedata.service.processor.request.ListReferenceCurrenciesRequest;
import com.paytech.platform.support.referencedata.service.processor.response.ReferenceCurrencyResult;

import lombok.AllArgsConstructor;

/** 查询参考数据币种并批量换取 logo 公开地址；只读，不开事务。 */
@Component
@AllArgsConstructor
public class ListReferenceCurrenciesProcessor
    extends AbstractProcessor<ListReferenceCurrenciesRequest, List<ReferenceCurrencyResult>> {

    private final ReferenceDataLogos logos;

    private final ReferenceCurrencyRepository repository;

    @Override
    protected List<ReferenceCurrencyResult> processRequest(ListReferenceCurrenciesRequest request) {
        List<ReferenceCurrency> rows = repository.find(request.getStatus(), request.getCodes());
        Map<String, String> urls = logos.publicUrls(rows.stream().map(ReferenceCurrency::getLogoFileAddress).toList());
        return rows.stream().map(row -> ReferenceCurrencyResult.of(row, urls.get(row.getLogoFileAddress()))).toList();
    }
}
