package com.paytech.reconciliation.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;
import com.paytech.reconciliation.api.summary.ReconSummaryApi;
import com.paytech.reconciliation.api.summary.response.ReconSummaryResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ReconExceptionConverter;
import com.paytech.reconciliation.domainservice.service.ReconExceptionQueryService;
import lombok.AllArgsConstructor;

/** 对账在途汇总的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class ReconSummaryController implements ReconSummaryApi {
    private final ReconExceptionConverter converter;
    private final ReconExceptionQueryService reconExceptionQueryService;

    /** 在途账单、开放异常账龄段与未匹配银行流水。 */
    @Override
    public ReconSummaryResponseBody getReconSummary(String entityNo, String currency) {
        return converter.toSummaryBody(entityNo, currency, reconExceptionQueryService.summarize(entityNo, currency));
    }
}
