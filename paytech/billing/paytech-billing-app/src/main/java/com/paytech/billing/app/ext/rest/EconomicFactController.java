package com.paytech.billing.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.request.ApproveEconomicFactRequestBody;
import com.paytech.billing.api.request.SubmitEconomicFactRequestBody;
import com.paytech.billing.api.response.EconomicFactListResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;
import com.paytech.billing.app.ext.rest.converter.EconomicFactConverter;
import com.paytech.billing.domain.EconomicFactBookScope;
import com.paytech.billing.domainservice.service.EconomicFactService;
import com.paytech.billing.service.processor.ApproveEconomicFactProcessor;
import com.paytech.billing.service.processor.SubmitEconomicFactProcessor;
import com.paytech.billing.service.processor.response.SubmitEconomicFactResponse;

import lombok.AllArgsConstructor;

/** 经济事实登记与读取的 HTTP 协议适配；查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class EconomicFactController implements EconomicFactCommandApi {
    private final EconomicFactService economicFactService;
    private final EconomicFactConverter converter;
    private final SubmitEconomicFactProcessor submitEconomicFactProcessor;
    private final ApproveEconomicFactProcessor approveEconomicFactProcessor;

    /** 新提交返回 201，同内容重复提交返回 200。 */
    @Override
    public ResponseEntity<EconomicFactResponseBody> submitEconomicFact(SubmitEconomicFactRequestBody body) {
        SubmitEconomicFactResponse response = submitEconomicFactProcessor.process(converter.toRequest(body));
        return ResponseEntity.status(response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(converter.toResponseBody(response.getFact()));
    }

    /** 批准经济事实。 */
    @Override
    public EconomicFactResponseBody approveEconomicFact(String factId, ApproveEconomicFactRequestBody body) {
        return converter.toResponseBody(approveEconomicFactProcessor.process(converter.toRequest(factId, body)));
    }

    /** 查询经济事实详情。 */
    @Override
    public EconomicFactResponseBody getEconomicFact(String factId) {
        return converter.toResponseBody(economicFactService.getFact(Long.parseLong(factId)));
    }

    /** 按账簿主体游标读取已批准事实。 */
    @Override
    public EconomicFactListResponseBody listEconomicFacts(String bookEntityType, String bookEntityId, String currency,
        String fromFactId, String limit) {
        EconomicFactBookScope scope = EconomicFactBookScope.of(bookEntityType, bookEntityId, currency);
        return converter.toResponseBody(
            economicFactService.listRecordedFacts(scope, converter.fromFactId(fromFactId), converter.limit(limit)));
    }

    /** 查询账簿主体下已批准事实的发布进度。 */
    @Override
    public PendingEconomicFactsResponseBody getPendingEconomicFacts(String bookEntityType, String bookEntityId,
        String currency) {
        EconomicFactBookScope scope = EconomicFactBookScope.of(bookEntityType, bookEntityId, currency);
        return converter.toResponseBody(economicFactService.getPublicationProgress(scope));
    }
}
