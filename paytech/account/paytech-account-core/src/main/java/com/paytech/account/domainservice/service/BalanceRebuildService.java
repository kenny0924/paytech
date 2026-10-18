package com.paytech.account.domainservice.service;

import java.util.List;

import com.paytech.account.domain.FactRecord;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domain.view.BalanceRebuildView;

/**
 * 按 billing 经济事实重算一个经营主体账簿的余额（design/04 §4 第 5 行）
 * <li>一个本地短事务：锁定账簿余额 → 确认没有晚于重放范围的新事实 → 删除重放范围内的处理记录与余额变化 → 余额归零 → 按 factId 升序重新应用。</li>
 * <li>调用方在事务外取得完整事实列表；本服务只读写 mor_account。</li>
 */
public interface BalanceRebuildService {

    /**
     * 重建账簿余额
     *
     * @param book 已经 ledger-api 确认的经营主体账簿；Finance Entity 账簿不保存 Owner 余额，拒绝重建
     * @param facts billing 返回的该账簿主体全部已批准（含已被替代）事实
     * @return 重放事实数、最大 factId 与重建后余额数；重建期间已有更晚的事实被处理时以并发冲突失败
     */
    BalanceRebuildView rebuild(LedgerBookRef book, List<FactRecord> facts);
}
