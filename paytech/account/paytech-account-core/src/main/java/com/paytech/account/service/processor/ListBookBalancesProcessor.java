package com.paytech.account.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domainservice.service.BalanceBookService;
import com.paytech.account.integration.ledger.LedgerBookClient;
import com.paytech.account.service.processor.request.LedgerBookRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 账簿内全部余额账户（design/04 §8）；账簿身份经 ledger-api 读取，失败关闭。 */
@Component
@AllArgsConstructor
public class ListBookBalancesProcessor extends AbstractProcessor<LedgerBookRequest, ListBookBalancesProcessor.Result> {
    private final LedgerBookClient ledgerBookClient;
    private final BalanceBookService balanceBookService;

    @Override
    protected Result processRequest(LedgerBookRequest request) {
        LedgerBookRef book = ledgerBookClient.getBook(request.getLedgerBookId());
        return new Result(book, balanceBookService.balances(book));
    }

    /**
     * 账簿与其余额
     *
     * @param book 账簿身份
     * @param balances 按科目代码、核算对象、余额桶排序的余额
     */
    public record Result(LedgerBookRef book, List<AccountBalance> balances) {
    }
}
