package com.paytech.ledger.app.ext.rest.converter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.ledger.api.response.PostingProgressResponseBody;
import com.paytech.ledger.domain.AccountPostingProgress;
import com.paytech.ledger.domain.BillingFactProgress;
import com.paytech.ledger.domain.BookPostingProgress;

/** 账簿入账进度的响应转换；计数、序号与 ID 输出为十进制字符串，不可用段的数字为空。 */
@Component
public class PostingProgressConverter {

    /** 两段进度。 */
    public PostingProgressResponseBody toResponseBody(BookPostingProgress progress) {
        PostingProgressResponseBody body = new PostingProgressResponseBody();
        body.setLedgerBookId(Long.toString(progress.getLedgerBookId()));
        body.setEntityType(progress.getEntityType().getCode());
        body.setEntityId(progress.getEntityId());
        body.setCurrency(progress.getCurrency());
        body.setFactSegment(factSegment(progress));
        PostingProgressResponseBody.StreamSegment streams = new PostingProgressResponseBody.StreamSegment();
        streams.setStatus(progress.getStreamStatus().getCode());
        streams.setStreams(streams(progress.getStreams()));
        body.setStreamSegment(streams);
        return body;
    }

    /** 计费事实段；UNAVAILABLE 时只有判定。 */
    static PostingProgressResponseBody.FactSegment factSegment(BookPostingProgress progress) {
        PostingProgressResponseBody.FactSegment segment = new PostingProgressResponseBody.FactSegment();
        segment.setStatus(progress.getFactStatus().getCode());
        BillingFactProgress billing = progress.getBilling();
        AccountPostingProgress account = progress.getAccount();
        if (billing == null || account == null) {
            return segment;
        }
        segment.setBillingPendingCount(Long.toString(billing.getPendingCount()));
        segment.setBillingMaxPendingFactId(text(billing.getMaxPendingFactId()));
        segment.setBillingPublishedCount(Long.toString(billing.getPublishedFactCount()));
        segment.setBillingMaxPublishedFactId(text(billing.getMaxPublishedFactId()));
        segment.setAccountAppliedCount(Long.toString(account.getAppliedFactCount()));
        segment.setAccountMaxAppliedFactId(text(account.getMaxAppliedFactId()));
        return segment;
    }

    /** 各流两侧进度。 */
    static List<PostingProgressResponseBody.Stream> streams(List<BookPostingProgress.StreamProgress> streams) {
        return streams.stream().map(PostingProgressConverter::stream).toList();
    }

    /** 一条流；流身份即 ledger 消费进度键，拆为主体、币种与分片号。 */
    static PostingProgressResponseBody.Stream stream(BookPostingProgress.StreamProgress progress) {
        AccountPostingProgress.Stream accountStream = progress.getAccountStream();
        String streamId = accountStream.getAggregateKey().aggregateId();
        AccountPostedPayload.StreamKey key = AccountPostedPayload.parseAggregateId(streamId);
        PostingProgressResponseBody.Stream body = new PostingProgressResponseBody.Stream();
        body.setStreamId(streamId);
        body.setEntityType(key.getEntityType().getCode());
        body.setEntityId(key.getEntityId());
        body.setCurrency(key.getCurrency());
        body.setShardNo(Integer.toString(key.getShardNo()));
        body.setAccountMaxSequence(Long.toString(accountStream.getMaxPostingSequence()));
        body.setAccountLastPostedTime(accountStream.getLastPostedTime());
        body.setLedgerConsumedSequence(Long.toString(progress.getConsumedSequence()));
        body.setLag(Long.toString(progress.lag()));
        body.setStatus(progress.getStatus().getCode());
        return body;
    }

    /** 可空数值转十进制文本。 */
    static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
