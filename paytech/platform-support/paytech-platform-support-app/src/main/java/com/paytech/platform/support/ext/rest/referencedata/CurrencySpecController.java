package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;
import com.paytech.platform.support.referencedata.service.processor.GetCurrencySpecProcessor;
import com.paytech.platform.support.referencedata.service.processor.ListCurrencySpecsProcessor;

import lombok.AllArgsConstructor;

/** 全平台币种规则查询的协议适配；不开事务。 */
@RestController
@AllArgsConstructor
public class CurrencySpecController implements CurrencySpecQueryApi {

    private final CurrencySpecConverter converter;
    private final GetCurrencySpecProcessor getProcessor;
    private final ListCurrencySpecsProcessor listProcessor;

    /** 全部规则。 */
    @Override
    public CurrencySpecListResponseBody listCurrencySpecs() {
        return converter.listResponse(listProcessor.process(converter.listRequest()));
    }

    /** 按币种代码查询。 */
    @Override
    public CurrencySpecResponseBody getCurrencySpec(String currency) {
        return converter.response(getProcessor.process(converter.getRequest(currency)));
    }
}
