package com.paytech.account.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domainservice.service.BalanceBookService;
import com.paytech.account.domainservice.service.BalanceReconciliationService;
import com.paytech.account.integration.ledger.LedgerBookClient;
import com.paytech.account.service.processor.request.ReconcileBalancesRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 日终核对（design/04 §7）：逐个存在余额的经营主体账簿比对本服务余额与 ledger 按分录聚合的余额
 * <li>每个账簿事务外经 ledger-api 读取账簿与 Owner 账户余额，再由 BalanceReconciliationService 写入差异；ledger 不可用时原样失败，
 * 由调度重试。</li>
 * <li>ledger 中找不到对应账簿时记 WARN 并跳过（余额来自经济事实，账簿缺失本身需要人工核实）。</li>
 */
@Component
@AllArgsConstructor
public class ReconcileBalancesProcessor extends AbstractProcessor<ReconcileBalancesRequest, Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(ReconcileBalancesProcessor.class);

    private final LedgerBookClient ledgerBookClient;
    private final BalanceBookService balanceBookService;
    private final BalanceReconciliationService balanceReconciliationService;

    @Override
    protected Integer processRequest(ReconcileBalancesRequest request) {
        int discrepancies = 0;
        for (BookIdentity identity : balanceBookService.bookIdentities()) {
            LedgerBookRef book = ledgerBookClient.findOperatingEntityBook(identity.entityId(), identity.currency());
            if (book == null) {
                LOG.warn("日终核对跳过: ledger 中没有余额所属的经营主体账簿 {}", identity);
                continue;
            }
            discrepancies += balanceReconciliationService.reconcile(request.getReconDate(), book,
                ledgerBookClient.accountTotals(book));
        }
        return discrepancies;
    }
}
