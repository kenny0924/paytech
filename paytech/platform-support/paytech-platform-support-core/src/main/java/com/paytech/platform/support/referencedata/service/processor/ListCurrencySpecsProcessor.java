package com.paytech.platform.support.referencedata.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecQueryService;
import com.paytech.platform.support.referencedata.service.processor.request.ListCurrencySpecsRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecResult;

import lombok.AllArgsConstructor;

/** 查询全部币种规则（D41 全平台参考数据）；只读，不开事务。 */
@Component
@AllArgsConstructor
public class ListCurrencySpecsProcessor extends AbstractProcessor<ListCurrencySpecsRequest, List<CurrencySpecResult>> {

    private final CurrencySpecQueryService queryService;

    @Override
    protected List<CurrencySpecResult> processRequest(ListCurrencySpecsRequest request) {
        return queryService.listCurrencySpecs().stream().map(CurrencySpecResult::of).toList();
    }
}
