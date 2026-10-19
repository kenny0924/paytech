package com.paytech.ledger.app.ext.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.PostingStreamCommandApi;
import com.paytech.ledger.api.request.CatchUpPostingStreamRequestBody;
import com.paytech.ledger.api.response.CatchUpPostingStreamResponseBody;
import com.paytech.ledger.app.ext.rest.converter.PostingStreamConverter;
import com.paytech.ledger.service.processor.PostingStreamCatchUpProcessor;
import com.paytech.ledger.service.processor.response.CatchUpPostingStreamResponse;

import lombok.AllArgsConstructor;

/** account 过账流追平的 HTTP 协议适配；命令完成后按操作人 Header 记一条 INFO 操作日志（不落库）。 */
@RestController
@AllArgsConstructor
public class PostingStreamController implements PostingStreamCommandApi {
    private static final Logger LOG = LoggerFactory.getLogger(PostingStreamController.class);

    private final PostingStreamConverter converter;
    private final PostingStreamCatchUpProcessor postingStreamCatchUpProcessor;

    /** 追平一条过账流。 */
    @Override
    public CatchUpPostingStreamResponseBody catchUpPostingStream(String actorAccountId, String sessionId,
        CatchUpPostingStreamRequestBody body) {
        CatchUpPostingStreamResponse response = postingStreamCatchUpProcessor.process(converter.toRequest(body));
        LOG.info("财务过账流命令 action={} actor={} session={} stream={}/{}/{}/{} processed={} lastSequence={} caughtUp={}",
            "CATCH_UP_POSTING_STREAM", actorAccountId, sessionId, body.getEntityType(), body.getEntityId(),
            body.getCurrency(), body.getShardNo(), response.getProcessedCount(), response.getLastSequence(),
            response.isCaughtUp());
        return converter.toResponseBody(response);
    }
}
