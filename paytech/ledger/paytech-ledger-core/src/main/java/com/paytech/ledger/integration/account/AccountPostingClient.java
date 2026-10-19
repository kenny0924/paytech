package com.paytech.ledger.integration.account;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.AccountPostingPage;
import com.paytech.ledger.domain.AccountPostingProgress;

/** 读取 account 入账进度与过账流批次；必须在数据库事务外调用。 */
public interface AccountPostingClient {

    /**
     * 查询账簿主体的事实应用进度与涉及的全部过账流
     *
     * @param book ledger 账簿（主体类型、主体标识与币种即 account 的主体参数）
     * @return 已应用事实数、最大 factId 与各流最大序号
     * @throws RuntimeException account 不可用、拒绝或响应不合法；调用方按失败关闭处理
     */
    AccountPostingProgress getProgress(LedgerBook book);

    /**
     * 按序号升序读取一条过账流从给定序号开始的批次（含流水）
     *
     * @param stream 过账流身份
     * @param fromSequence 起始序号（含）
     * @param limit 本页最多批次数，1–200
     * @return 已校验的批次与下一页起始序号
     * @throws RuntimeException account 不可用、响应不合法或批次内容非法
     */
    AccountPostingPage listPostings(AccountPostedPayload.StreamKey stream, long fromSequence, int limit);
}
