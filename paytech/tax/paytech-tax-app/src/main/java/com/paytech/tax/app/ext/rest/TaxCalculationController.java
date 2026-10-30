package com.paytech.tax.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.FailException;
import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.tax.app.ext.rest.converter.TaxCalculationConverter;
import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domainservice.service.TaxQueryService;
import com.paytech.tax.service.processor.CalculateTaxProcessor;

import lombok.AllArgsConstructor;

/** 税务计算的 HTTP 入站适配：计算委托 Processor，按 ID 读取委托查询 Service。 */
@RestController
@AllArgsConstructor
public class TaxCalculationController implements TaxCalculationApi {
    private final TaxQueryService taxQueryService;
    private final CalculateTaxProcessor calculateTaxProcessor;
    private final TaxCalculationConverter taxCalculationConverter;

    /** 转换请求、计算并转换结果。 */
    @Override
    public TaxCalculationResponseBody calculateTax(CalculateTaxRequestBody body) {
        TaxCalculationView view = calculateTaxProcessor.process(taxCalculationConverter.toRequest(body));
        return taxCalculationConverter.toBody(view);
    }

    /** 不存在返回 404 TAX_CALCULATION_NOT_FOUND。 */
    @Override
    public TaxCalculationResponseBody getTaxCalculation(String calculationId) {
        TaxCalculationView view = taxQueryService.findCalculation(Long.parseLong(calculationId));
        if (view == null) {
            throw new FailException(TaxErrorCode.TAX_CALCULATION_NOT_FOUND, "税务计算不存在: " + calculationId);
        }
        return taxCalculationConverter.toBody(view);
    }
}
