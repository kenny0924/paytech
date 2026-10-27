package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.referencedata.service.processor.ListReferenceCurrenciesProcessor;

import lombok.AllArgsConstructor;

/** 参考数据币种查询的协议适配；不开事务。 */
@RestController
@AllArgsConstructor
public class ReferenceCurrencyController implements ReferenceCurrencyQueryApi {

    private final ReferenceDataQueryConverter converter;

    private final ListReferenceCurrenciesProcessor processor;

    /** 按可选状态与编码查询。 */
    @Override
    public ReferenceCurrencyListResponseBody listCurrencies(String status, String codes) {
        return converter.currencyList(processor.process(converter.currencies(status, codes)));
    }
}
