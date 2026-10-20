package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.FinanceBillingFactClient;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.BillingFactResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.BillingFactSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceBillingFactModels.SubmitBillingFactRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * billing 计费事实的 HTTP 转发：每个入口只调用一次 billing，不重试
 * <li>命令把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；不转发浏览器 Authorization、Cookie 或 Idempotency-Key。</li>
 * <li>登记固定 sourceSystem=OPERATIONS，每次生成新的 sourceReference（OPS- + UUID），页面不传；HTTP 重试由 Gateway Idempotency-Key 承担。</li>
 * <li>批准与作废无请求体；详情与命令回执的事实 ID 须与请求一致。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceBillingFactClientImpl implements FinanceBillingFactClient {
    /** Operations 控制台登记的来源系统。 */
    static final String SOURCE_SYSTEM = "OPERATIONS";

    /** Operations 控制台来源引用前缀。 */
    static final String SOURCE_REFERENCE_PREFIX = "OPS-";

    private final BillingFactQueryApi queryApi;
    private final BillingFactCommandApi commandApi;

    /** 事实检索分页，核对分页回执。 */
    @Override
    public PagingResponseBody<BillingFactSummaryResponseBody> searchBillingFacts(String entityNo, String currency,
        String clientId, String status, String factType, Instant createdFrom, Instant createdTo, int page, int size) {
        return page(queryApi.searchBillingFacts(entityNo, currency, clientId, status, factType, createdFrom, createdTo,
            page, size), page, size, BillingFactSummaryResponseBody.class);
    }

    /** 事实详情；事实 ID 须与请求一致。 */
    @Override
    public BillingFactResponseBody getBillingFact(String factId) {
        return checked(factId, queryApi.getBillingFact(factId));
    }

    /** 登记事实：固定来源系统并生成来源引用；回执须带新事实 ID。 */
    @Override
    public BillingFactResponseBody submitBillingFact(OperatorContext context, SubmitBillingFactRequestBody request) {
        var body = convert(request, com.paytech.billing.api.request.SubmitBillingFactRequestBody.class);
        body.setSourceSystem(SOURCE_SYSTEM);
        body.setSourceReference(SOURCE_REFERENCE_PREFIX + UUID.randomUUID());
        var submitted = write(request, () -> commandApi.submitBillingFact(context.accountId(), context.sessionId(), body)
            .getBody(), BillingFactResponseBody.class);
        requireIdentity(submitted.getFactId());
        return submitted;
    }

    /** 批准事实，无请求体。 */
    @Override
    public BillingFactResponseBody approveBillingFact(OperatorContext context, String factId) {
        return checked(factId, commandApi.approveBillingFact(context.accountId(), context.sessionId(), factId));
    }

    /** 作废草稿，无请求体。 */
    @Override
    public BillingFactResponseBody voidBillingFact(OperatorContext context, String factId) {
        return checked(factId, commandApi.voidBillingFact(context.accountId(), context.sessionId(), factId));
    }

    /** 转换事实并核对事实 ID 与明细行。 */
    private static BillingFactResponseBody checked(String factId, Object value) {
        var fact = convert(value, BillingFactResponseBody.class);
        requireSame(factId, fact.getFactId());
        FinanceContractConverter.require(fact.getLines() != null);
        return fact;
    }
}
