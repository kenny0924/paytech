package com.paytech.platform.support.referencedata.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecQueryService;
import com.paytech.platform.support.referencedata.service.processor.request.GetCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecResult;

import lombok.AllArgsConstructor;

/** 按币种代码查询规则（D41）；代码格式由基础校验拒绝，不存在返回 404；只读，不开事务。 */
@Component
@AllArgsConstructor
public class GetCurrencySpecProcessor extends AbstractProcessor<GetCurrencySpecRequest, CurrencySpecResult> {

    private final CurrencySpecQueryService queryService;

    @Override
    protected CurrencySpecResult processRequest(GetCurrencySpecRequest request) {
        return CurrencySpecResult.of(queryService.getCurrencySpec(request.getCurrency()));
    }
}
