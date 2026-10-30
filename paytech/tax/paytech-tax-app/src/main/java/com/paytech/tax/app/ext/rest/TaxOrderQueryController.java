package com.paytech.tax.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.FailException;
import com.paytech.tax.api.TaxOrderQueryApi;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.api.response.TaxOrderResponseBody;
import com.paytech.tax.app.ext.rest.converter.TaxOrderConverter;
import com.paytech.tax.domain.view.TaxOrderView;
import com.paytech.tax.domainservice.service.TaxQueryService;

import lombok.AllArgsConstructor;

/** 订单税务查询的 HTTP 入站适配（Operations BFF 调用），直接委托查询 Service。 */
@RestController
@AllArgsConstructor
public class TaxOrderQueryController implements TaxOrderQueryApi {
    private final TaxQueryService taxQueryService;
    private final TaxOrderConverter taxOrderConverter;

    /** 没有任何计算返回 404 TAX_ORDER_NOT_FOUND。 */
    @Override
    public TaxOrderResponseBody getOrderTax(String orderNo) {
        TaxOrderView view = taxQueryService.findOrderTax(orderNo);
        if (view == null) {
            throw new FailException(TaxErrorCode.TAX_ORDER_NOT_FOUND, "订单没有税务记录: " + orderNo);
        }
        return taxOrderConverter.toBody(view);
    }
}
