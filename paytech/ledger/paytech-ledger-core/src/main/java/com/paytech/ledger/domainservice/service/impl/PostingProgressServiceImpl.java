package com.paytech.ledger.domainservice.service.impl;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.messaging.api.ConsumedEventRepository;
import com.paytech.ledger.api.enums.PostingProgressStatus;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.AccountPostingProgress;
import com.paytech.ledger.domain.BillingFactProgress;
import com.paytech.ledger.domain.BookPostingProgress;
import com.paytech.ledger.domainservice.service.PostingProgressService;

import lombok.AllArgsConstructor;

/** 两段入账进度判定；已消费序号逐流各一个本库短事务读取（与原关账步骤 3 相同）。 */
@Service
@AllArgsConstructor
public class PostingProgressServiceImpl implements PostingProgressService {
    private final TransactionTemplate transactionTemplate;
    private final ConsumedEventRepository consumedEventRepository;

    /** 段 ① 需要两方进度，段 ② 只需要 account 进度。 */
    @Override
    public BookPostingProgress evaluate(LedgerBook book, BillingFactProgress billing, AccountPostingProgress account) {
        boolean factAvailable = billing != null && account != null;
        PostingProgressStatus factStatus = factAvailable ? factStatus(billing, account) : PostingProgressStatus.UNAVAILABLE;
        if (account == null) {
            return new BookPostingProgress(book.getId(), book.getEntityType(), book.getEntityId(), book.getCurrency(),
                factStatus, null, null, PostingProgressStatus.UNAVAILABLE, List.of());
        }
        List<BookPostingProgress.StreamProgress> streams = account.getStreams().stream().map(this::streamProgress)
            .toList();
        return new BookPostingProgress(book.getId(), book.getEntityType(), book.getEntityId(), book.getCurrency(),
            factStatus, factAvailable ? billing : null, factAvailable ? account : null, streamStatus(streams), streams);
    }

    /** 段 ①：billing 无未发布事实，account 已应用事实数与最大 factId 等于 billing 已发布事实数与最大 factId。 */
    static PostingProgressStatus factStatus(BillingFactProgress billing, AccountPostingProgress account) {
        boolean consistent = billing.getPendingCount() == 0 && account.getAppliedFactCount() == billing.getPublishedFactCount()
            && Objects.equals(account.getMaxAppliedFactId(), billing.getMaxPublishedFactId());
        return consistent ? PostingProgressStatus.CONSISTENT : PostingProgressStatus.BEHIND;
    }

    /** 段 ② 单流：ledger 已消费序号（本库短事务内读取）等于 account 最大序号。 */
    BookPostingProgress.StreamProgress streamProgress(AccountPostingProgress.Stream stream) {
        long consumed = transactionTemplate.execute(
            status -> consumedEventRepository.lastVersion(stream.getAggregateKey())).orElse(0L);
        PostingProgressStatus status = consumed == stream.getMaxPostingSequence() ? PostingProgressStatus.CONSISTENT
            : PostingProgressStatus.BEHIND;
        return new BookPostingProgress.StreamProgress(stream, consumed, status);
    }

    /** 段 ② 判定取各流中最差的一个；没有流时为 CONSISTENT。 */
    static PostingProgressStatus streamStatus(List<BookPostingProgress.StreamProgress> streams) {
        return streams.stream().anyMatch(stream -> stream.getStatus() == PostingProgressStatus.BEHIND)
            ? PostingProgressStatus.BEHIND : PostingProgressStatus.CONSISTENT;
    }
}
