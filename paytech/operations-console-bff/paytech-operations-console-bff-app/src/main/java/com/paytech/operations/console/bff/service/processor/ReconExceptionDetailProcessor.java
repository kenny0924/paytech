package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.ReconciliationCashClient;
import com.paytech.operations.console.bff.integration.ReconciliationClearingClient;
import com.paytech.operations.console.bff.integration.ReconciliationExceptionClient;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionDetailResponseBody.AccountLabel;
import com.paytech.operations.console.bff.service.processor.request.ReconExceptionDetailRequest;

import lombok.AllArgsConstructor;

/**
 * 对账异常详情页：reconciliation 异常详情（异常、来源对象摘要、可用处置与归类码、处置历史）+ 所属账户的可读标识
 * <li>交易层异常补清算账户编码与渠道，银行层异常补银行账户名称与末四位（同为 reconciliation 只读接口）；两者都没有时不补。</li>
 * <li>来源对象摘要由 reconciliation 给出，BFF 不再逐类回读来源对象；归类码范围以 reconciliation 返回为准。</li>
 * <li>任一读取失败按原错误整页失败；只读组合，不编排任何写入。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconExceptionDetailProcessor
    extends AbstractProcessor<ReconExceptionDetailRequest, ReconExceptionDetailResponseBody> {
    private final ReconciliationCashClient cashAccounts;
    private final ReconciliationClearingClient clearingAccounts;
    private final ReconciliationExceptionClient exceptions;

    @Override
    protected ReconExceptionDetailResponseBody processRequest(ReconExceptionDetailRequest request) {
        ReconExceptionDetailResponseBody detail = exceptions.getReconException(request.getExceptionId());
        detail.setAccount(accountLabel(detail.getException().getClearingAccountId(),
            detail.getException().getCashAccountId()));
        return detail;
    }

    /** 按异常所属账户读取可读标识；清算账户优先，两者都为空时返回空。 */
    AccountLabel accountLabel(String clearingAccountId, String cashAccountId) {
        var label = new AccountLabel();
        if (clearingAccountId != null) {
            var account = clearingAccounts.getClearingAccount(clearingAccountId);
            label.setClearingAccountCode(account.getClearingAccountCode());
            label.setChannelCode(account.getChannelCode());
            return label;
        }
        if (cashAccountId != null) {
            var account = cashAccounts.getCashAccount(cashAccountId);
            label.setCashAccountName(account.getAccountName());
            label.setAccountLast4(account.getAccountLast4());
            return label;
        }
        return null;
    }
}
