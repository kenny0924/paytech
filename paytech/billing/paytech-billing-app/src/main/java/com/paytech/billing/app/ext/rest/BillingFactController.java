package com.paytech.billing.app.ext.rest;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.request.SubmitBillingFactRequestBody;
import com.paytech.billing.api.response.BillingFactListResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;
import com.paytech.billing.api.response.BillingFactSummaryResponseBody;
import com.paytech.billing.app.ext.rest.converter.BillingFactConverter;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domainservice.service.BillingFactQueryService;
import com.paytech.billing.service.processor.ApproveBillingFactProcessor;
import com.paytech.billing.service.processor.SubmitBillingFactProcessor;
import com.paytech.billing.service.processor.VoidBillingFactProcessor;
import com.paytech.billing.service.processor.response.SubmitBillingFactResponse;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 计费事实登记与读取的 HTTP 协议适配；写入委托 Processor，查询直接委托本地 Service
 * <li>命令的操作人取自 X-Gateway-Actor-Subject，成功后记一条含操作人与会话的命令日志（作废人只在此留痕）。</li>
 */
@RestController
@AllArgsConstructor
public class BillingFactController implements BillingFactCommandApi, BillingFactQueryApi {
    private static final Logger LOG = LoggerFactory.getLogger(BillingFactController.class);

    private final BillingFactConverter converter;
    private final BillingFactQueryService billingFactQueryService;
    private final VoidBillingFactProcessor voidBillingFactProcessor;
    private final SubmitBillingFactProcessor submitBillingFactProcessor;
    private final ApproveBillingFactProcessor approveBillingFactProcessor;

    /** 新提交返回 201，同内容重复提交返回 200。 */
    @Override
    public ResponseEntity<BillingFactResponseBody> submitBillingFact(String operatorId, String sessionId,
        SubmitBillingFactRequestBody body) {
        SubmitBillingFactResponse response = submitBillingFactProcessor.process(converter.toRequest(operatorId, body));
        BillingFactResponseBody responseBody = converter.toResponseBody(response.getFact());
        logCommand("SUBMIT_BILLING_FACT", operatorId, sessionId, responseBody.getFactId());
        return ResponseEntity.status(response.isCreated() ? HttpStatus.CREATED : HttpStatus.OK).body(responseBody);
    }

    /** 批准计费事实。 */
    @Override
    public BillingFactResponseBody approveBillingFact(String operatorId, String sessionId, String factId) {
        BillingFactResponseBody responseBody = converter.toResponseBody(
            approveBillingFactProcessor.process(converter.toApproveRequest(operatorId, factId)));
        logCommand("APPROVE_BILLING_FACT", operatorId, sessionId, factId);
        return responseBody;
    }

    /** 作废计费事实草稿。 */
    @Override
    public BillingFactResponseBody voidBillingFact(String operatorId, String sessionId, String factId) {
        BillingFactResponseBody responseBody =
            converter.toResponseBody(voidBillingFactProcessor.process(converter.toVoidRequest(factId)));
        logCommand("VOID_BILLING_FACT", operatorId, sessionId, factId);
        return responseBody;
    }

    /** 管理列表：按 OE、币种与可选条件分页检索任意状态事实头。 */
    @Override
    public PagingResponseBody<BillingFactSummaryResponseBody> searchBillingFacts(String entityNo, String currency,
        String clientId, String status, String factType, Instant createdFrom, Instant createdTo, Integer page,
        Integer size) {
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return billingFactQueryService.searchFacts(
            converter.toSearchCriteria(entityNo, currency, clientId, status, factType, createdFrom, createdTo), paging)
            .map(converter::toSummaryBody);
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

    /** 命令成功日志：动作、操作人、会话与事实 ID。 */
    void logCommand(String action, String operatorId, String sessionId, String factId) {
        LOG.info("计费事实命令 action={} actor={} session={} factId={}", action, operatorId, sessionId, factId);
    }
}
