package com.paytech.reconciliation.app.ext.rest.converter;

import org.springframework.stereotype.Component;
import com.paytech.reconciliation.api.cash.request.CreateCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.request.ReviewCashAccountRequestBody;
import com.paytech.reconciliation.api.cash.response.CashAccountResponseBody;
import com.paytech.reconciliation.api.cash.response.CurrentCashAccountResponseBody;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.service.processor.request.CreateCashAccountRequest;
import com.paytech.reconciliation.service.processor.request.ReviewCashAccountRequest;

/** 银行账户 HTTP 协议转换；响应只带末四位，从不包含令牌或明文。 */
@Component
public class CashAccountConverter {

    /** 新建命令（含明文账号，只交给 Processor）。 */
    public CreateCashAccountRequest toCreateRequest(String actorAccountId, CreateCashAccountRequestBody body) {
        return new CreateCashAccountRequest(Long.valueOf(actorAccountId),
            ReconciliationWire.parse(ReconEntityType.class, body.getEntityType()), body.getEntityId(),
            ReconciliationWire.parse(AccountPurpose.class, body.getAccountPurpose()), body.getBankCode(),
            body.getAccountName(), body.getCurrency(), body.getAccountNumber());
    }

    /** 复核命令。 */
    public ReviewCashAccountRequest toReviewRequest(String actorAccountId, String cashAccountId,
        ReviewCashAccountRequestBody body) {
        return new ReviewCashAccountRequest(Long.valueOf(actorAccountId), Long.valueOf(cashAccountId),
            ReconciliationWire.parse(CheckerDecision.class, body.getDecision()), body.getApprovedContentHash());
    }

    /** 银行账户响应。 */
    public CashAccountResponseBody toResponseBody(CashAccount account) {
        CashAccountResponseBody body = new CashAccountResponseBody();
        body.setCashAccountId(account.getId().toString());
        body.setEntityType(account.getEntityType().getCode());
        body.setEntityId(account.getEntityId());
        body.setAccountPurpose(account.getAccountPurpose().getCode());
        body.setBankCode(account.getBankCode());
        body.setAccountName(account.getAccountName());
        body.setCurrency(account.getCurrency());
        body.setAccountLast4(account.getAccountLast4());
        body.setStatus(account.getStatus().getCode());
        body.setPendingAction(ReconciliationWire.code(account.getPendingAction()));
        body.setContentHash(account.getContentHash());
        body.setMakerId(account.getMakerId().toString());
        body.setCheckerId(ReconciliationWire.text(account.getCheckerId()));
        body.setVersion(account.getVersion().toString());
        body.setCreateTime(account.getCreateTime());
        body.setUpdateTime(account.getUpdateTime());
        return body;
    }

    /** 当前账户响应：只有主键与末四位。 */
    public CurrentCashAccountResponseBody toCurrentResponseBody(CashAccount account) {
        CurrentCashAccountResponseBody body = new CurrentCashAccountResponseBody();
        body.setCashAccountId(account.getId().toString());
        body.setAccountLast4(account.getAccountLast4());
        return body;
    }
}
