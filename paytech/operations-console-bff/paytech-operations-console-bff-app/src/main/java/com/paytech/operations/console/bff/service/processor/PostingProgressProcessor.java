package com.paytech.operations.console.bff.service.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.api.enums.PostingProgressStatus;
import com.paytech.operations.console.bff.integration.FinanceLedgerBookClient;
import com.paytech.operations.console.bff.integration.FinanceSubledgerClient;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingProgressResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.PostingSummary;
import com.paytech.operations.console.bff.service.model.FinancePostingOpsModels.Stream;
import com.paytech.operations.console.bff.service.processor.request.PostingProgressRequest;

import lombok.AllArgsConstructor;

/**
 * 明细账运维的账簿入账进度聚合（B1）：先读 ledger 两段进度，再为每条落后的流补充"卡住的批次"
 * <li>两段判定由 ledger 给出（与关账步骤 CHECK_ACCOUNT_POSTING_PROGRESS 同口径），BFF 不重新比对。</li>
 * <li>ledger 是聚合入口，读取失败整页失败（不可达 503、超时 504、账簿不存在 400）；billing / account 不可用由 ledger 标为 UNAVAILABLE。</li>
 * <li>卡住的批次：按流串行调用 account 批次列表（起始序号 = ledger 已消费序号 + 1，limit 1）；单条读取失败或 account 尚无该批次时为空，
 * 不影响其他流与整页。只读组合，不开事务、不编排写入。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PostingProgressProcessor extends AbstractProcessor<PostingProgressRequest, PostingProgressResponseBody> {
    private static final Logger LOG = LoggerFactory.getLogger(PostingProgressProcessor.class);

    /** 卡住的批次只取起始序号处的一个批次。 */
    static final int STUCK_POSTING_LIMIT = 1;

    private final FinanceLedgerBookClient ledgerClient;
    private final FinanceSubledgerClient subledgerClient;

    @Override
    protected PostingProgressResponseBody processRequest(PostingProgressRequest request) {
        PostingProgressResponseBody progress = ledgerClient.getPostingProgress(request.getLedgerBookId());
        for (Stream stream : progress.getStreamSegment().getStreams()) {
            if (PostingProgressStatus.BEHIND.getCode().equals(stream.getStatus())) {
                stream.setStuckPosting(findStuckPosting(request.getLedgerBookId(), stream));
            }
        }
        return progress;
    }

    /**
     * 读取落后流的下一个待消费批次
     *
     * @return 批次摘要；流身份或序号不合法、account 读取失败或尚无该批次时为 null
     */
    PostingSummary findStuckPosting(String ledgerBookId, Stream stream) {
        try {
            AccountPostedPayload.StreamKey key = AccountPostedPayload.parseAggregateId(stream.getStreamId());
            long fromSequence = Long.parseLong(stream.getLedgerConsumedSequence()) + 1;
            var page = subledgerClient.listStreamPostings(key, fromSequence, STUCK_POSTING_LIMIT);
            return page.getItems().isEmpty() ? null : page.getItems().getFirst();
        } catch (RuntimeException unavailable) {
            LOG.warn("明细账运维: 卡住的批次读取失败, 账簿 {}, 流 {}, {}", ledgerBookId, stream.getStreamId(),
                unavailable.getClass().getSimpleName());
            return null;
        }
    }
}
