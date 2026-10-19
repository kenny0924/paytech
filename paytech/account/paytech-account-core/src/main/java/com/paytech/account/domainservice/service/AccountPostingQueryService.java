package com.paytech.account.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.account.domain.view.PostingProgressView;

/**
 * 入账批次与进度读取：AccountPosted 发布、ledger 追平与关账进度、控制台批次详情共用
 * <li>只读，不加锁、不开事务；批次一旦写入不再变化（发布时间除外），多次读取不需要一致快照。</li>
 */
public interface AccountPostingQueryService {

    /**
     * 主体与币种下的事实应用进度与涉及的全部入账流
     *
     * @param entityType 主体类型
     * @param entityId 主体标识；FINANCE_ENTITY 为十进制主键
     * @param currency 币种
     * @return 进度；FINANCE_ENTITY 时流包含 financeEntityId 等于该主体的事实所在的 OE 流
     */
    PostingProgressView progress(AccountEntityType entityType, String entityId, String currency);

    /**
     * 按流与起始序号读取批次
     *
     * @return 按序号升序、含全部流水的批次，最多 limit 条
     */
    List<AccountPostingView> findPostings(AccountEntityType entityType, String entityId, String currency, int shardNo,
        long fromSequence, int limit);

    /**
     * 按流与序号读取一个批次
     *
     * @return 批次视图；不存在为空
     */
    AccountPostingView findPosting(AccountEntityType entityType, String entityId, String currency, int shardNo,
        long postingSequence);

    /**
     * 按主键读取一个批次
     *
     * @return 批次视图；不存在为空
     */
    AccountPostingView findPosting(long postingId);

    /** 首次发布确认时写入批次发布时间；单条条件更新，不开显式事务。 */
    void markPublished(AccountPosting posting, Instant publishedTime);
}
