package com.paytech.ledger.app.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.ledger.api.request.CatchUpPostingStreamRequestBody;
import com.paytech.ledger.api.response.CatchUpPostingStreamResponseBody;
import com.paytech.ledger.service.processor.request.CatchUpPostingStreamRequest;
import com.paytech.ledger.service.processor.response.CatchUpPostingStreamResponse;

/** 过账流追平命令的请求解析与结果转换；计数与序号输出为十进制字符串。 */
@Component
public class PostingStreamConverter {
    /** 未传 maxBatches 时本次最多处理的批次数。 */
    static final int DEFAULT_MAX_BATCHES = 200;

    /** 请求体转内部请求；maxBatches 缺省为 {@value #DEFAULT_MAX_BATCHES}。 */
    public CatchUpPostingStreamRequest toRequest(CatchUpPostingStreamRequestBody body) {
        CatchUpPostingStreamRequest request = new CatchUpPostingStreamRequest();
        request.setEntityType(CodeEnumConverter.parse(AccountEntityType.class, body.getEntityType()));
        request.setEntityId(body.getEntityId());
        request.setCurrency(body.getCurrency());
        request.setShardNo(body.getShardNo());
        request.setMaxBatches(body.getMaxBatches() == null ? DEFAULT_MAX_BATCHES : body.getMaxBatches());
        return request;
    }

    /** 转换追平结果。 */
    public CatchUpPostingStreamResponseBody toResponseBody(CatchUpPostingStreamResponse response) {
        CatchUpPostingStreamResponseBody body = new CatchUpPostingStreamResponseBody();
        body.setProcessedCount(Integer.toString(response.getProcessedCount()));
        body.setLastSequence(Long.toString(response.getLastSequence()));
        body.setCaughtUp(response.isCaughtUp());
        return body;
    }
}
