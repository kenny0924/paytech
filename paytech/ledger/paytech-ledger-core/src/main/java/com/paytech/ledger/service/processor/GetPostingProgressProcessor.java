package com.paytech.ledger.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.AccountPostingProgress;
import com.paytech.ledger.domain.BillingFactProgress;
import com.paytech.ledger.domain.BookPostingProgress;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.PostingProgressService;
import com.paytech.ledger.integration.account.AccountPostingClient;
import com.paytech.ledger.integration.billing.BillingFactClient;
import com.paytech.ledger.service.processor.request.GetPostingProgressRequest;

import lombok.AllArgsConstructor;

/**
 * 账簿入账进度查询（{@code GET /posting-progress/v1/ledger-books/{ledgerBookId}}）
 * <li>只读：不开显式事务、不加锁。账簿不存在 LEDGER_BOOK_NOT_FOUND。</li>
 * <li>billing、account 进度在事务外分别读取，任一方失败只让对应段为 UNAVAILABLE（需求 B5），不像关账那样整本失败关闭；
 * 判定交给与关账步骤 3 共用的 {@link PostingProgressService}。</li>
 */
@Component
@AllArgsConstructor
public class GetPostingProgressProcessor extends AbstractProcessor<GetPostingProgressRequest, BookPostingProgress> {
    private static final Logger LOG = LoggerFactory.getLogger(GetPostingProgressProcessor.class);

    private final BillingFactClient billingFactClient;
    private final AccountPostingClient accountPostingClient;
    private final LedgerBookRepository ledgerBookRepository;
    private final PostingProgressService postingProgressService;

    @Override
    protected BookPostingProgress processRequest(GetPostingProgressRequest request) {
        LedgerBook book = getLedgerBook(request.getLedgerBookId());
        return postingProgressService.evaluate(book, readBillingProgress(book), readAccountProgress(book));
    }

    /** 账簿必须存在。 */
    LedgerBook getLedgerBook(long ledgerBookId) {
        LedgerBook book = ledgerBookRepository.findById(ledgerBookId);
        if (book == null) {
            throw new FailException(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
        }
        return book;
    }

    /**
     * 事务外读取 billing 进度
     *
     * @return billing 进度；billing 不可用、拒绝或响应不合法时为 null（只影响计费事实段）
     */
    BillingFactProgress readBillingProgress(LedgerBook book) {
        try {
            return billingFactClient.getProgress(book);
        } catch (RuntimeException unavailable) {
            LOG.warn("账簿入账进度: billing 进度不可用, 账簿 {}, {}", book.getId(), unavailable.toString());
            return null;
        }
    }

    /**
     * 事务外读取 account 进度
     *
     * @return account 进度；account 不可用、拒绝或响应不合法时为 null（计费事实段与过账流段都为 UNAVAILABLE）
     */
    AccountPostingProgress readAccountProgress(LedgerBook book) {
        try {
            return accountPostingClient.getProgress(book);
        } catch (RuntimeException unavailable) {
            LOG.warn("账簿入账进度: account 进度不可用, 账簿 {}, {}", book.getId(), unavailable.toString());
            return null;
        }
    }
}
