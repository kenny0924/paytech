package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ReconciliationClearingClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.AddClearingAccountMidRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ChangeClearingAccountStatusRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.CreateClearingAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.UnassignedMidListResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.UpdateClearingAccountSettingsRequestBody;
import com.paytech.reconciliation.api.clearing.ClearingAccountApi;

import lombok.AllArgsConstructor;

/**
 * reconciliation 清算账户的 HTTP 转发：每个入口只调用一次 reconciliation，不重试
 * <li>命令把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；不转发浏览器 Authorization、Cookie 或
 * Idempotency-Key。</li>
 * <li>详情与命令回执的清算账户主键须与请求一致并带 MID 集合；新建回执须带新主键且编码一致。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconciliationClearingClientImpl implements ReconciliationClearingClient {
    private final ClearingAccountApi api;

    /** 清算账户分页，核对分页回执。 */
    @Override
    public PagingResponseBody<ClearingAccountSummaryResponseBody> listClearingAccounts(String entityNo,
        String channelCode, String currency, String status, int page, int size) {
        return page(api.listClearingAccounts(entityNo, channelCode, currency, status, page, size), page, size,
            ClearingAccountSummaryResponseBody.class);
    }

    /** 清算账户详情；主键一致并带 MID 集合。 */
    @Override
    public ClearingAccountResponseBody getClearingAccount(String clearingAccountId) {
        return checked(clearingAccountId, convert(api.getClearingAccount(clearingAccountId),
            ClearingAccountResponseBody.class));
    }

    /** 未归属 MID；条目集合不可缺失。 */
    @Override
    public UnassignedMidListResponseBody listUnassignedMids(String entityNo, String channelCode, String currency) {
        var unassigned = convert(api.listUnassignedMids(entityNo, channelCode, currency),
            UnassignedMidListResponseBody.class);
        require(unassigned.getItems() != null);
        return unassigned;
    }

    /** 新建清算账户；回执带新主键、编码与请求一致。 */
    @Override
    public ClearingAccountResponseBody createClearingAccount(OperatorContext context,
        CreateClearingAccountRequestBody request) {
        var created = write(request, () -> api.createClearingAccount(context.accountId(), context.sessionId(),
            convert(request, com.paytech.reconciliation.api.clearing.request.CreateClearingAccountRequestBody.class))
            .getBody(), ClearingAccountResponseBody.class);
        requireIdentity(created.getClearingAccountId());
        requireSame(request.getClearingAccountCode(), created.getClearingAccountCode());
        require(created.getMids() != null);
        return created;
    }

    /** 修改配置；回执主键一致。 */
    @Override
    public ClearingAccountResponseBody updateClearingAccountSettings(OperatorContext context, String clearingAccountId,
        UpdateClearingAccountSettingsRequestBody request) {
        return checked(clearingAccountId, write(request, () -> api.updateClearingAccountSettings(context.accountId(),
            context.sessionId(), clearingAccountId, convert(request,
                com.paytech.reconciliation.api.clearing.request.UpdateClearingAccountSettingsRequestBody.class)),
            ClearingAccountResponseBody.class));
    }

    /** 挂入 MID；回执主键一致。 */
    @Override
    public ClearingAccountResponseBody addClearingAccountMid(OperatorContext context, String clearingAccountId,
        AddClearingAccountMidRequestBody request) {
        return checked(clearingAccountId, write(request, () -> api.addClearingAccountMid(context.accountId(),
            context.sessionId(), clearingAccountId, convert(request,
                com.paytech.reconciliation.api.clearing.request.AddClearingAccountMidRequestBody.class)),
            ClearingAccountResponseBody.class));
    }

    /** 摘除 MID；回执主键一致。 */
    @Override
    public ClearingAccountResponseBody removeClearingAccountMid(OperatorContext context, String clearingAccountId,
        String midId) {
        return checked(clearingAccountId, convert(api.removeClearingAccountMid(context.accountId(),
            context.sessionId(), clearingAccountId, midId), ClearingAccountResponseBody.class));
    }

    /** 启停清算账户；回执主键一致。 */
    @Override
    public ClearingAccountResponseBody changeClearingAccountStatus(OperatorContext context, String clearingAccountId,
        ChangeClearingAccountStatusRequestBody request) {
        return checked(clearingAccountId, write(request, () -> api.changeClearingAccountStatus(context.accountId(),
            context.sessionId(), clearingAccountId, convert(request,
                com.paytech.reconciliation.api.clearing.request.ChangeClearingAccountStatusRequestBody.class)),
            ClearingAccountResponseBody.class));
    }

    /** 回执主键须与请求一致，且 MID 集合不可缺失。 */
    private static ClearingAccountResponseBody checked(String clearingAccountId, ClearingAccountResponseBody account) {
        requireSame(clearingAccountId, account.getClearingAccountId());
        require(account.getMids() != null);
        return account;
    }
}
