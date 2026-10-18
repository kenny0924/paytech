package com.paytech.ledger.domainservice.service;

import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.domain.JournalPostingContext;

/**
 * 记账内核：把一条 VALIDATED 指令写成不可变 Journal
 * <li>必须在调用方已开启的本地事务内执行，与指令来源的状态变更一起提交或回滚。</li>
 * <li>锁顺序固定为：期间（共享）→ 账簿序号（排他）；第 11b 步起不锁账户、不写余额（design/04 §4）。</li>
 */
public interface JournalPostingService {

    /**
     * 过账一条指令
     *
     * @param context 指令、指令行、期间与过账日期
     * @return 新的分录头
     */
    JournalEntry post(JournalPostingContext context);
}
