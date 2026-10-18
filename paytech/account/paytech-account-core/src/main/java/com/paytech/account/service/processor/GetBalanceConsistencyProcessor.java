package com.paytech.account.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.account.domain.view.BookConsistencyView;
import com.paytech.account.domainservice.service.BalanceBookService;
import com.paytech.account.integration.ledger.LedgerBookClient;
import com.paytech.account.service.processor.request.LedgerBookRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 账簿的余额消费进度（design/04 §6，ledger 关账步骤 2、3 调用）
 * <li>账簿身份经 ledger-api 读取，失败关闭；进度只读本服务库，不加锁。</li>
 */
@Component
@AllArgsConstructor
public class GetBalanceConsistencyProcessor extends AbstractProcessor<LedgerBookRequest, BookConsistencyView> {
    private final LedgerBookClient ledgerBookClient;
    private final BalanceBookService balanceBookService;

    @Override
    protected BookConsistencyView processRequest(LedgerBookRequest request) {
        return balanceBookService.consistency(ledgerBookClient.getBook(request.getLedgerBookId()));
    }
}
