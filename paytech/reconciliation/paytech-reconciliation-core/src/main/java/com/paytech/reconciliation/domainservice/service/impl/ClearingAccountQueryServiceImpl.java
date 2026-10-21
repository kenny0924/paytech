package com.paytech.reconciliation.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountQueryService;

import lombok.AllArgsConstructor;

/** 清算账户查询；列表每页至多 200 个账户，逐个读取其 MID。 */
@Service
@AllArgsConstructor
public class ClearingAccountQueryServiceImpl implements ClearingAccountQueryService {
    private final ClearingAccountRepository clearingAccountRepository;
    private final ClearingAccountMidRepository clearingAccountMidRepository;

    /** 读取详情。 */
    @Override
    public ClearingAccountView getClearingAccount(long clearingAccountId) {
        ClearingAccount account = clearingAccountRepository.findById(clearingAccountId);
        if (account == null) {
            throw new BizCheckFailException("清算账户不存在: " + clearingAccountId);
        }
        return new ClearingAccountView(account, clearingAccountMidRepository.listByClearingAccount(clearingAccountId));
    }

    /** 分页查询。 */
    @Override
    public PagingResponseBody<ClearingAccountView> listClearingAccounts(String entityNo, String channelCode,
        String currency, ClearingAccountStatus status, PagingRequestBody paging) {
        List<ClearingAccountView> items = clearingAccountRepository.findPage(entityNo, channelCode, currency, status,
            paging.offset(), paging.size()).stream().map(account -> new ClearingAccountView(account,
                clearingAccountMidRepository.listByClearingAccount(account.getId()))).toList();
        long total = clearingAccountRepository.countPage(entityNo, channelCode, currency, status);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 按 MID + 币种解析。 */
    @Override
    public ClearingAccount resolve(long midId, String currency) {
        ClearingAccountMid mid = clearingAccountMidRepository.findByMidAndCurrency(midId, currency);
        ClearingAccount account = mid == null ? null : clearingAccountRepository.findById(mid.getClearingAccountId());
        if (account == null || account.getStatus() != ClearingAccountStatus.ACTIVE) {
            throw new FailException(ReconciliationErrorCode.CLEARING_ACCOUNT_NOT_RESOLVED,
                "未解析到有效的清算账户: MID " + midId + ", 币种 " + currency);
        }
        return account;
    }
}
