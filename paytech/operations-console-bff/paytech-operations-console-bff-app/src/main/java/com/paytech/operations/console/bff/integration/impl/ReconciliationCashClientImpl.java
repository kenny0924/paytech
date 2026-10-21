package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationCashClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.CashAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.CreateCashAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationCashModels.ReviewCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.CashAccountApi;

import lombok.AllArgsConstructor;

/**
 * reconciliation 银行账户的 HTTP 转发：每个入口只调用一次 reconciliation，不重试
 * <li>新建请求的账号明文只在本次转发中出现，不进入异常消息或日志；回执只含末四位。</li>
 * <li>详情与命令回执的银行账户主键须与请求一致；新建回执须带新主键。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconciliationCashClientImpl implements ReconciliationCashClient {
    private final CashAccountApi api;

    /** 银行账户分页，核对分页回执。 */
    @Override
    public PagingResponseBody<CashAccountResponseBody> listCashAccounts(String entityType, String entityId,
        String currency, String accountPurpose, String status, int page, int size) {
        return page(api.listCashAccounts(entityType, entityId, currency, accountPurpose, status, page, size), page,
            size, CashAccountResponseBody.class);
    }

    /** 银行账户详情；主键一致。 */
    @Override
    public CashAccountResponseBody getCashAccount(String cashAccountId) {
        var account = convert(api.getCashAccount(cashAccountId), CashAccountResponseBody.class);
        requireSame(cashAccountId, account.getCashAccountId());
        return account;
    }

    /** 提交新建；回执带新主键。 */
    @Override
    public CashAccountResponseBody createCashAccount(OperatorContext context, CreateCashAccountRequestBody request) {
        var created = write(request, () -> api.createCashAccount(context.accountId(), context.sessionId(),
            convert(request, com.paytech.reconciliation.api.cash.request.CreateCashAccountRequestBody.class)).getBody(),
            CashAccountResponseBody.class);
        requireIdentity(created.getCashAccountId());
        return created;
    }

    /** 申请关闭；回执主键一致。 */
    @Override
    public CashAccountResponseBody requestCashAccountClosure(OperatorContext context, String cashAccountId) {
        var account = convert(api.requestCashAccountClosure(context.accountId(), context.sessionId(), cashAccountId),
            CashAccountResponseBody.class);
        requireSame(cashAccountId, account.getCashAccountId());
        return account;
    }

    /** 复核；回执主键一致。 */
    @Override
    public CashAccountResponseBody reviewCashAccount(OperatorContext context, String cashAccountId,
        ReviewCashAccountRequestBody request) {
        var account = write(request, () -> api.reviewCashAccount(context.accountId(), context.sessionId(),
            cashAccountId, convert(request, com.paytech.reconciliation.api.cash.request.ReviewCashAccountRequestBody.class)),
            CashAccountResponseBody.class);
        requireSame(cashAccountId, account.getCashAccountId());
        return account;
    }
}
