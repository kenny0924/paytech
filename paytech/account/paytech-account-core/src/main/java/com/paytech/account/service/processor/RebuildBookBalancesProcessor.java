package com.paytech.account.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domain.view.BalanceRebuildView;
import com.paytech.account.domainservice.service.BalanceRebuildService;
import com.paytech.account.integration.billing.EconomicFactReplayClient;
import com.paytech.account.integration.ledger.LedgerBookClient;
import com.paytech.account.service.processor.request.RebuildBookBalancesRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 从 billing 重放经济事实重算一个经营主体账簿的余额（design/04 §4 第 5 行，{@code POST /balances/v1/books/{bookId}/rebuild}）
 * <li>事务外：经 ledger-api 读取账簿身份（Finance Entity 账簿不保存 Owner 余额，直接拒绝），经 billing-api 分页读取该账簿主体的全部已批准事实；
 * 任一不可用即失败。</li>
 * <li>事务内：由 BalanceRebuildService 一个短事务重写余额与处理记录；重放期间有更晚的事实被处理时以并发冲突失败，调用方重试。</li>
 */
@Component
@AllArgsConstructor
public class RebuildBookBalancesProcessor extends AbstractProcessor<RebuildBookBalancesRequest, BalanceRebuildView> {
    private static final Logger LOG = LoggerFactory.getLogger(RebuildBookBalancesProcessor.class);

    private final LedgerBookClient ledgerBookClient;
    private final EconomicFactReplayClient economicFactReplayClient;
    private final BalanceRebuildService balanceRebuildService;

    @Override
    protected BalanceRebuildView processRequest(RebuildBookBalancesRequest request) {
        LedgerBookRef book = ledgerBookClient.getBook(request.getLedgerBookId());
        if (book.identity().entityType() != BookEntityType.OPERATING_ENTITY) {
            throw new BizCheckFailException("Owner 余额只在经营主体账簿维护，不能重建: 账簿 " + book.ledgerBookId());
        }
        BalanceRebuildView result = balanceRebuildService.rebuild(book,
            economicFactReplayClient.listFacts(book.identity()));
        LOG.info("账簿余额已重建: 账簿 {}, 操作人 {}, 事实 {}, 余额 {}", book.ledgerBookId(), request.getOperatorId(),
            result.replayedFactCount(), result.balanceCount());
        return result;
    }
}
