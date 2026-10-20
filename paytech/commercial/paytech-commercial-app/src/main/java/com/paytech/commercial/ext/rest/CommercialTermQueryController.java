package com.paytech.commercial.ext.rest;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.term.CommercialTermQueryApi;
import com.paytech.commercial.api.term.response.CommercialTermPlanSummary;
import com.paytech.commercial.api.term.response.CommercialTermVersionSummary;
import com.paytech.commercial.service.processor.CommercialTermPlanListQueryProcessor;
import com.paytech.commercial.service.processor.CommercialTermVersionListQueryProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 商业条款方案 / 版本列表的 HTTP 协议适配：参数转换后委托对应只读 Processor。 */
@RestController
@AllArgsConstructor
public class CommercialTermQueryController implements CommercialTermQueryApi {
    private final CommercialTermPlanListQueryProcessor commercialTermPlanListQueryProcessor;
    private final CommercialTermVersionListQueryProcessor commercialTermVersionListQueryProcessor;

    /** 方案分页列表。 */
    @Override
    public PagingResponseBody<CommercialTermPlanSummary> listPlans(String agreementId, String clientId,
        String localPartnerId, String termType, String status, Integer page, Integer size) {
        return commercialTermPlanListQueryProcessor.process(CommercialTermQueryConverter.planQuery(agreementId, clientId,
            localPartnerId, termType, status, page, size)).map(CommercialTermQueryConverter::planSummary);
    }

    /** 版本分页列表与审批工作台。 */
    @Override
    public PagingResponseBody<CommercialTermVersionSummary> listVersions(String planId, String agreementId,
        List<String> status, String effectiveFrom, String effectiveTo, Integer page, Integer size) {
        return commercialTermVersionListQueryProcessor.process(CommercialTermQueryConverter.versionQuery(planId,
            agreementId, status, effectiveFrom, effectiveTo, page, size)).map(CommercialTermQueryConverter::versionSummary);
    }
}
