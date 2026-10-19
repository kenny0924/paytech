package com.paytech.account.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.account.api.request.RebuildPostingStreamsRequestBody;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.account.api.response.PostingStreamsRebuildResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * account 过账流与过账批次（明细账 Owner：account）
 * <li>进度：ledger 关账步骤 CHECK_ACCOUNT_POSTING_PROGRESS 读取，比对 billing → account 与 account → ledger 两段。</li>
 * <li>批次列表：ledger 追平（{@code POST /posting-streams/v1/catch-up}）按流、按序号分页读取，结构与 AccountPosted 载荷一致。</li>
 * <li>批次详情：Operations 控制台批次抽屉；按流重建：运营按 t_account_entry 重算余额，不删不改流水。</li>
 * <li>查询只读，不加锁、不开事务；金额为 Money（主货币单位字符串，按币种精度补齐），ID、版本与序号为十进制字符串。</li>
 * <li>内部接口：未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/account-postings/v1", accept = "application/json")
public interface AccountPostingApi {

    /**
     * 查询主体与币种下的事实应用进度与各过账流最大序号
     *
     * @param entityType 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 主体标识：OE 为 entityNo，Finance Entity 为十进制主键
     * @param currency 大写三字母币种
     * @return 已应用事实数与最大 factId（只计自身事件已到达的事实，排除 PENDING_SUPERSEDED），以及该账簿涉及的全部流；
     *         <li>FINANCE_ENTITY 主体时 streams 同时包含 financeEntityId 等于该主体的事实所在的 OE 流。</li>
     *         <li>没有任何流时 streams 为空列表、计数为 0，不报错。</li>
     */
    @GetExchange("/progress")
    AccountPostingProgressResponseBody getPostingProgress(
        @RequestParam("entityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam("entityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String entityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency);

    /**
     * 按过账流与起始序号读取批次（含流水），按序号升序
     *
     * @param entityType 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 主体标识
     * @param currency 大写三字母币种
     * @param shardNo 流分片号，非负十进制
     * @param fromSequence 起始序号（含），正十进制；ledger 传已消费序号 + 1
     * @param limit 可选每页批次数，1–200，默认 200；超过 200 时 400 INVALID_REQUEST
     * @return 序号 ≥ fromSequence 的批次及下一页起始序号；最后一页 nextFromSequence 为空；流不存在时返回空页
     */
    @GetExchange
    AccountPostingListResponseBody listPostings(
        @RequestParam("entityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam("entityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String entityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam("shardNo") @Pattern(regexp = "0|[1-9][0-9]{0,8}") String shardNo,
        @RequestParam("fromSequence") @Pattern(regexp = "[1-9][0-9]{0,18}") String fromSequence,
        @RequestParam(value = "limit", required = false) @Pattern(regexp = "[1-9][0-9]{0,2}") String limit);

    /**
     * 查询过账批次详情
     *
     * @param postingId 批次主键
     * @return 批次头与按行号升序的全部流水（带科目代码、Owner 与余额桶）；批次不存在 400 POSTING_NOT_FOUND
     */
    @GetExchange("/{postingId}")
    AccountPostingResponseBody getPosting(
        @PathVariable("postingId") @Pattern(regexp = "[1-9][0-9]{0,18}") String postingId);

    /**
     * 按过账流重建主体与币种下全部 Owner 账户余额
     * <li>逐流锁 t_account_posting_stream 行，按 t_account_entry 重算该流全部账户，以旧 balance_version CAS 并加一；不删不改流水，不发布事件。</li>
     *
     * @param body 主体、币种与发起人
     * @return 重算的账户数、流水数与最大批次序号；主体与币种下没有过账流 400 STREAM_NOT_FOUND；与入账并发冲突 409
     */
    @PostExchange("/streams/rebuild")
    PostingStreamsRebuildResponseBody rebuildPostingStreams(@Valid @RequestBody RebuildPostingStreamsRequestBody body);
}
