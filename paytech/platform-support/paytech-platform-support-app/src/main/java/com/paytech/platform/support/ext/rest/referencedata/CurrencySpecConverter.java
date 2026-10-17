package com.paytech.platform.support.ext.rest.referencedata;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;
import com.paytech.platform.support.referencedata.service.processor.request.GetCurrencySpecRequest;
import com.paytech.platform.support.referencedata.service.processor.request.ListCurrencySpecsRequest;
import com.paytech.platform.support.referencedata.service.processor.response.CurrencySpecResult;

/** 币种规则查询的协议转换；枚举按 Code 输出。 */
@Component
public class CurrencySpecConverter {

    /** 无参数的全部查询。 */
    public ListCurrencySpecsRequest listRequest() {
        return new ListCurrencySpecsRequest();
    }

    /** Path 中的币种代码原样交给 Processor 校验。 */
    public GetCurrencySpecRequest getRequest(String currency) {
        return new GetCurrencySpecRequest(currency);
    }

    /** 保持 Owner 给出的升序。 */
    public CurrencySpecListResponseBody listResponse(List<CurrencySpecResult> results) {
        CurrencySpecListResponseBody body = new CurrencySpecListResponseBody();
        body.setItems(results.stream().map(this::response).toList());
        return body;
    }

    /** 单条规则。 */
    public CurrencySpecResponseBody response(CurrencySpecResult result) {
        CurrencySpecResponseBody body = new CurrencySpecResponseBody();
        body.setCurrency(result.getCurrency());
        body.setMinorUnit(result.getMinorUnit());
        body.setRoundingMode(result.getRoundingMode().getCode());
        body.setStatus(result.getStatus().getCode());
        body.setUpdateTime(result.getUpdateTime().toString());
        return body;
    }
}
