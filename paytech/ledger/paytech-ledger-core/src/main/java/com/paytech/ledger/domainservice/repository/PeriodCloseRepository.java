package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;
import com.paytech.ledger.domain.view.PeriodCloseView;

/** t_period_close：关账/重开流程根读写与关账前置检查。 */
public interface PeriodCloseRepository {

    /** 插入流程根；同一期间已有活动流程或序号重复时按并发冲突拒绝。 */
    void insert(PeriodClose close);

    /** 同一期间的下一个关账序号。 */
    int nextCloseNo(long periodId);

    /** 按期间与序号读取流程根，不存在返回空。 */
    PeriodClose findByPeriodAndNo(long periodId, int closeNo);

    /** 按主键读取流程根，不存在返回空。 */
    PeriodClose findById(long id);

    /** 读取期间内指定类型序号最大的流程根，不存在返回空。 */
    PeriodClose findLatestByType(long periodId, PeriodCloseType closeType);

    /** 按原状态与版本迁移状态并写入活动槽、摘要与复核人，成功后同步原对象。 */
    void updateStatus(PeriodClose close, PeriodCloseStatus status, Integer activeSlot, String closeHash, Long checkerId);

    /** 读取流程摘要，不存在返回空。 */
    PeriodCloseView findView(long periodId, int closeNo);

    /** 按序号降序分页读取流程摘要。 */
    List<PeriodCloseView> findViewPage(long periodId, int size, int offset);

    /** 统计期间的流程数。 */
    long countByPeriod(long periodId);

    /** 统计待执行或可重试的补偿任务。 */
    long countPendingCompensationEvents();

    /** 统计账簿内余额非零的渠道与银行账户。 */
    long countNonZeroTreasuryAccounts(List<Long> bookIds);
}
