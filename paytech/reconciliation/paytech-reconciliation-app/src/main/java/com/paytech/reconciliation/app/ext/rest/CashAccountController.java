package com.paytech.reconciliation.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.cash.CashAccountApi;
import com.paytech.reconciliation.api.cash.request.CreateCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.request.ReviewCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.response.CashAccountResponseBody;
import com.paytech.reconciliation.api.cash.response.CurrentCashAccountResponseBody;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.app.ext.rest.converter.CashAccountConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.service.processor.CreateCashAccountProcessor;
import com.paytech.reconciliation.service.processor.RequestCashAccountClosureProcessor;
import com.paytech.reconciliation.service.processor.ReviewCashAccountProcessor;
import com.paytech.reconciliation.service.processor.request.CashAccountCommandRequest;
import lombok.AllArgsConstructor;

/** 银行账户维护、复核与查询的 HTTP 协议适配：写入口委托 Processor，查询委托查询 Service。 */
@RestController
@AllArgsConstructor
public class CashAccountController implements CashAccountApi {
    private final CashAccountConverter converter;
    private final ReviewCashAccountProcessor reviewCashAccountProcessor;
    private final CreateCashAccountProcessor createCashAccountProcessor;
    private final BankReconciliationQueryService bankReconciliationQueryService;
    private final RequestCashAccountClosureProcessor requestCashAccountClosureProcessor;

    /** 提交新建并返回 201。 */
    @Override
    public ResponseEntity<CashAccountResponseBody> createCashAccount(String actorAccountId, String sessionId,
        CreateCashAccountRequestBody body) {
        CashAccountResponseBody response = converter.toResponseBody(
            createCashAccountProcessor.process(converter.toCreateRequest(actorAccountId, body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 申请关闭。 */
    @Override
    public CashAccountResponseBody requestCashAccountClosure(String actorAccountId, String sessionId,
        String cashAccountId) {
        return converter.toResponseBody(requestCashAccountClosureProcessor.process(
            new CashAccountCommandRequest(Long.valueOf(actorAccountId), Long.valueOf(cashAccountId))));
    }

    /** 复核。 */
    @Override
    public CashAccountResponseBody reviewCashAccount(String actorAccountId, String sessionId, String cashAccountId,
        ReviewCashAccountRequestBody body) {
        return converter.toResponseBody(reviewCashAccountProcessor.process(
            converter.toReviewRequest(actorAccountId, cashAccountId, body)));
    }

    /** 分页查询。 */
    @Override
    public PagingResponseBody<CashAccountResponseBody> listCashAccounts(String entityType, String entityId,
        String currency, String accountPurpose, String status, Integer page, Integer size) {
        return bankReconciliationQueryService.listCashAccounts(ReconciliationWire.parse(ReconEntityType.class, entityType),
            entityId, currency, ReconciliationWire.parse(AccountPurpose.class, accountPurpose),
            ReconciliationWire.parse(CashAccountStatus.class, status), PagingRequestBody.of(page, size))
            .map(converter::toResponseBody);
    }

    /** 详情。 */
    @Override
    public CashAccountResponseBody getCashAccount(String cashAccountId) {
        return converter.toResponseBody(bankReconciliationQueryService.getCashAccount(Long.parseLong(cashAccountId)));
    }

    /** 当前 ACTIVE 账户（服务间内部接口）。 */
    @Override
    public CurrentCashAccountResponseBody getCurrentCashAccount(String entityType, String entityId, String currency,
        String purpose) {
        return converter.toCurrentResponseBody(bankReconciliationQueryService.getCurrentCashAccount(
            ReconciliationWire.parse(ReconEntityType.class, entityType), entityId, currency,
            ReconciliationWire.parse(AccountPurpose.class, purpose)));
    }
}
