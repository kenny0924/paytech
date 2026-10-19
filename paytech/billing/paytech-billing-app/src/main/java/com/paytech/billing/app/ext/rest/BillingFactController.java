package com.paytech.billing.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.request.ApproveBillingFactRequestBody;
import com.paytech.billing.api.request.SubmitBillingFactRequestBody;
import com.paytech.billing.api.response.BillingFactListResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;
import com.paytech.billing.app.ext.rest.converter.BillingFactConverter;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domainservice.service.BillingFactQueryService;
import com.paytech.billing.service.processor.ApproveBillingFactProcessor;
import com.paytech.billing.service.processor.SubmitBillingFactProcessor;
import com.paytech.billing.service.processor.response.SubmitBillingFactResponse;

import lombok.AllArgsConstructor;

/** 计费事实登记与读取的 HTTP 协议适配；写入委托 Processor，查询直接委托本地 Service。 */
@RestController
@AllArgsConstructor
public class BillingFactController implements BillingFactCommandApi, BillingFactQueryApi {
    private final BillingFactConverter converter;
    private final BillingFactQueryService billingFactQueryService;
    private final SubmitBillingFactProcessor submitBillingFactProcessor;
    private final ApproveBillingFactProcessor approveBillingFactProcessor;

    /** 新提交返回 201，同内容重复提交返回 200。 */
    @Override
    public ResponseEntity<BillingFactResponseBody> submitBillingFact(SubmitBillingFactRequestBody body) {
        SubmitBillingFactResponse response = submitBillingFactProcessor.process(converter.toRequest(body));
        return ResponseEntity.status(response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(converter.toResponseBody(response.getFact()));
    }

    /** 批准计费事实。 */
    @Override
    public BillingFactResponseBody approveBillingFact(String factId, ApproveBillingFactRequestBody body) {
        return converter.toResponseBody(approveBillingFactProcessor.process(converter.toRequest(factId, body)));
    }

    /** 查询计费事实详情。 */
    @Override
    public BillingFactResponseBody getBillingFact(String factId) {
        return converter.toResponseBody(billingFactQueryService.getFact(Long.parseLong(factId)));
    }

    /** 按账簿主体游标读取已批准事实。 */
    @Override
    public BillingFactListResponseBody listBillingFacts(String entityType, String entityId, String currency,
        String fromFactId, String limit) {
        BillingFactBookScope scope = BillingFactBookScope.of(entityType, entityId, currency);
        return converter.toResponseBody(billingFactQueryService.listRecordedFacts(scope, converter.fromFactId(fromFactId),
            converter.bookLimit(limit)));
    }

    /** 查询账簿主体下已批准事实的发布进度。 */
    @Override
    public BillingFactProgressResponseBody getBillingFactProgress(String entityType, String entityId, String currency) {
        BillingFactBookScope scope = BillingFactBookScope.of(entityType, entityId, currency);
        return converter.toResponseBody(billingFactQueryService.getPublicationProgress(scope));
    }

    /** 按外部业务对象引用游标读取任意状态事实。 */
    @Override
    public BillingFactListResponseBody listBillingFactsByRef(String refType, String refId, String fromFactId,
        String limit) {
        return converter.toResponseBody(billingFactQueryService.listFactsByRef(converter.refType(refType), refId,
            converter.fromFactId(fromFactId), converter.refLimit(limit)));
    }
}
