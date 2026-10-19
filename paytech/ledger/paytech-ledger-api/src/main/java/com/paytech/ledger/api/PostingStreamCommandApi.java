package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CatchUpPostingStreamRequestBody;
import com.paytech.ledger.api.response.CatchUpPostingStreamResponseBody;

import jakarta.validation.Valid;

/**
 * account 过账流的追平命令（内部能力 Ledger & Account Balance）
 * <li>AccountPosted 因首条序号未到、缺口或投递上限进入死信时的恢复路径：按流经 account-api {@code GET /account-postings/v1} 从已消费序号 + 1
 * 分页读取，逐批次按与消费相同的逻辑过账，每批次一个本库事务并推进消费进度；之后重放的死信消息按 STALE 跳过。</li>
 * <li>Finance 内部接口：命令操作人身份来自 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header；本期不接控制台，不得直接对外发布。</li>
 */
@HttpExchange(url = "/posting-streams/v1", accept = "application/json")
public interface PostingStreamCommandApi {

    /**
     * 追平一条 account 过账流
     *
     * @param actorAccountId 操作人 Account ID，填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param body 流身份与本次最多处理的批次数
     * @return 本次处理的批次数、处理后的已消费序号与是否已追平；
     *         <li>批次所需期间无可用 OPEN 期间或与消费并发冲突 409，已处理的批次保留，可再次调用继续。</li>
     *         <li>account 或 billing 不可用 503。</li>
     */
    @PostExchange("/catch-up")
    CatchUpPostingStreamResponseBody catchUpPostingStream(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CatchUpPostingStreamRequestBody body);
}
