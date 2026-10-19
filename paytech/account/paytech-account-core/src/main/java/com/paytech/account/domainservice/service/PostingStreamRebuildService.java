package com.paytech.account.domainservice.service;

import java.util.List;

import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.domain.model.StreamRebuildResult;

/**
 * 按入账流从流水重算 Owner 账户余额（technique 2.1「account 规则与目录」余额重建，设计 4.5）
 * <li>每条流一个本地短事务：锁流行 → 锁该流的全部账户 → 按 t_account_entry 重算借贷合计与净额 → 以旧 balance_version CAS 并加一。</li>
 * <li>不删不改流水，不改账户身份与状态，不发布事件。</li>
 */
public interface PostingStreamRebuildService {

    /**
     * 重算一组流涉及的全部账户
     *
     * @param streams 主体与币种下的全部流，非空
     * @return 重算的账户数、流水数与最大批次序号
     */
    StreamRebuildResult rebuild(List<AccountPostingStream> streams);
}
