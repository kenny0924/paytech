package com.paytech.reconciliation.app.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.reconciliation.api.clearing.request.CreateClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.request.UpdateClearingAccountSettingsRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResponseBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountSummaryResponseBody;
import com.paytech.reconciliation.api.clearing.response.UnassignedMidListResponseBody;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.domain.UnassignedMid;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.service.processor.request.CreateClearingAccountRequest;
import com.paytech.reconciliation.service.processor.request.UpdateClearingAccountSettingsRequest;

/** 清算账户 HTTP 协议转换：Body 与内部命令、领域结果与响应 Body。 */
@Component
public class ClearingAccountConverter {

    /** 新建命令。 */
    public CreateClearingAccountRequest toCreateRequest(CreateClearingAccountRequestBody body) {
        return CreateClearingAccountRequest.builder().clearingAccountCode(body.getClearingAccountCode())
            .channelCode(body.getChannelCode()).entityNo(body.getEntityNo()).currency(body.getCurrency())
            .settlementCashAccountId(ReconciliationWire.id(body.getSettlementCashAccountId()))
            .unmatchedWaitDays(body.getUnmatchedWaitDays())
            .costToleranceAmount(ReconciliationWire.decimal(body.getCostToleranceAmount()))
            .cashGraceDays(body.getCashGraceDays()).build();
    }

    /** 修改配置命令。 */
    public UpdateClearingAccountSettingsRequest toSettingsRequest(String clearingAccountId,
        UpdateClearingAccountSettingsRequestBody body) {
        return UpdateClearingAccountSettingsRequest.builder().clearingAccountId(ReconciliationWire.id(clearingAccountId))
            .settlementCashAccountId(ReconciliationWire.id(body.getSettlementCashAccountId()))
            .unmatchedWaitDays(body.getUnmatchedWaitDays())
            .costToleranceAmount(ReconciliationWire.decimal(body.getCostToleranceAmount()))
            .cashGraceDays(body.getCashGraceDays()).build();
    }

    /** 详情响应。 */
    public ClearingAccountResponseBody toResponseBody(ClearingAccountView view) {
        ClearingAccount account = view.getAccount();
        ClearingAccountResponseBody body = new ClearingAccountResponseBody();
        body.setClearingAccountId(account.getId().toString());
        body.setClearingAccountCode(account.getClearingAccountCode());
        body.setChannelCode(account.getChannelCode());
        body.setEntityNo(account.getEntityNo());
        body.setCurrency(account.getCurrency());
        body.setSettlementCashAccountId(ReconciliationWire.text(account.getSettlementCashAccountId()));
        body.setUnmatchedWaitDays(account.getUnmatchedWaitDays());
        body.setCostToleranceAmount(ReconciliationWire.amount(account.getCurrency(), account.getCostToleranceAmount()));
        body.setCashGraceDays(account.getCashGraceDays());
        body.setStatus(account.getStatus().getCode());
        body.setVersion(account.getVersion().toString());
        body.setCreateTime(account.getCreateTime());
        body.setUpdateTime(account.getUpdateTime());
        body.setMids(view.getMids().stream().map(ClearingAccountConverter::toMid).toList());
        return body;
    }

    /** 列表项。 */
    public ClearingAccountSummaryResponseBody toSummaryResponseBody(ClearingAccountView view) {
        ClearingAccount account = view.getAccount();
        ClearingAccountSummaryResponseBody body = new ClearingAccountSummaryResponseBody();
        body.setClearingAccountId(account.getId().toString());
        body.setClearingAccountCode(account.getClearingAccountCode());
        body.setChannelCode(account.getChannelCode());
        body.setEntityNo(account.getEntityNo());
        body.setCurrency(account.getCurrency());
        body.setSettlementCashAccountId(ReconciliationWire.text(account.getSettlementCashAccountId()));
        body.setStatus(account.getStatus().getCode());
        body.setMidCount(view.getMids().size());
        body.setUpdateTime(account.getUpdateTime());
        return body;
    }

    /** 解析结果。 */
    public ClearingAccountResolutionResponseBody toResolutionResponseBody(ClearingAccount account) {
        ClearingAccountResolutionResponseBody body = new ClearingAccountResolutionResponseBody();
        body.setClearingAccountId(account.getId().toString());
        body.setClearingAccountCode(account.getClearingAccountCode());
        body.setChannelCode(account.getChannelCode());
        body.setEntityNo(account.getEntityNo());
        body.setCurrency(account.getCurrency());
        return body;
    }

    /** 未归属 MID 列表。 */
    public UnassignedMidListResponseBody toUnassignedResponseBody(List<UnassignedMid> items) {
        UnassignedMidListResponseBody body = new UnassignedMidListResponseBody();
        body.setItems(items.stream().map(item -> {
            UnassignedMidListResponseBody.UnassignedMid mid = new UnassignedMidListResponseBody.UnassignedMid();
            mid.setMidId(String.valueOf(item.getMid().getMidId()));
            mid.setExternalMidCode(item.getMid().getExternalMidCode());
            mid.setChannelCode(item.getMid().getChannelCode());
            mid.setEntityNo(item.getMid().getEntityNo());
            mid.setCurrency(item.getCurrency());
            return mid;
        }).toList());
        return body;
    }

    /** 一条 MID 归属。 */
    static ClearingAccountResponseBody.Mid toMid(ClearingAccountMid mid) {
        ClearingAccountResponseBody.Mid body = new ClearingAccountResponseBody.Mid();
        body.setMidId(mid.getMidId().toString());
        body.setCreateTime(mid.getCreateTime());
        return body;
    }
}
