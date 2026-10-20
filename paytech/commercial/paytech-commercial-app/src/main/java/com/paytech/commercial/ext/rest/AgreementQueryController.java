package com.paytech.commercial.ext.rest;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.agreement.AgreementQueryApi;
import com.paytech.commercial.api.agreement.response.AgreementSummary;
import com.paytech.commercial.service.processor.AgreementListQueryProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 合同列表的 HTTP 协议适配：参数转换后委托 AgreementListQueryProcessor。 */
@RestController
@AllArgsConstructor
public class AgreementQueryController implements AgreementQueryApi {
    private final AgreementListQueryProcessor agreementListQueryProcessor;

    /** 合同分页列表。 */
    @Override
    public PagingResponseBody<AgreementSummary> listAgreements(String clientId, String localPartnerId,
        String agreementType, List<String> status, String entityNo, String agreementNo, Integer page, Integer size) {
        return agreementListQueryProcessor.process(AgreementQueryConverter.query(clientId, localPartnerId, agreementType,
            status, entityNo, agreementNo, page, size)).map(AgreementQueryConverter::summary);
    }
}
