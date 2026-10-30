package com.paytech.ledger.domainservice.service;

import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.AccountPostingProgress;
import com.paytech.ledger.domain.BillingFactProgress;
import com.paytech.ledger.domain.BookPostingProgress;

/**
 * 按账簿判定 billing → account、account → ledger 两段入账进度（技术方案 2.1、设计 8.8）
 * <li>期间关账步骤 CHECK_ACCOUNT_POSTING_PROGRESS 与账簿入账进度查询共用，保证页面结论与关账结论同口径。</li>
 * <li>billing / account 进度由调用方在事务外读取后传入；本服务只按流以本库短事务读取 ledger 已消费序号，不访问外部依赖。</li>
 */
public interface PostingProgressService {

    /**
     * 判定一本账簿的两段进度
     *
     * @param book 账簿
     * @param billing billing 进度；billing 不可用时传 null
     * @param account account 进度；account 不可用时传 null
     * @return 两段判定、各流两侧序号与落后数；任一方为 null 时对应段为 UNAVAILABLE，不抛异常
     */
    BookPostingProgress evaluate(LedgerBook book, BillingFactProgress billing, AccountPostingProgress account);
}
