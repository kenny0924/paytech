package com.paytech.ledger.domainservice.service;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;

/** 试算异常处置：投影重建的发起与批准（本地保存，不写 Journal）。 */
public interface TrialExceptionService {

    /** 把 OPEN 异常以投影重建方式交付复核：记录解决方式与编制人，状态迁移为 UNDER_REVIEW。 */
    void startProjectionRebuild(TrialBalanceException exception, long makerId);

    /**
     * 批准投影重建
     * <li>一个事务内锁定账户、从全部分录重算四个投影值并以余额版本 CAS 写回，再把异常迁移为 RESOLVED。</li>
     * <li>不写 Journal、不改变序号；版本或状态已变化时整体回滚并以并发冲突失败。</li>
     *
     * @param exception 状态为 UNDER_REVIEW 的异常
     * @param checkerId 复核人，调用方已校验不同于编制人
     * @param minorUnit 账簿币种小数位，用于解决摘要
     */
    void approveProjectionRebuild(TrialBalanceException exception, long checkerId, int minorUnit);

    /**
     * 引用账户所属账簿币种的小数位
     *
     * @param accountId 异常引用的账户主键
     * @return 币种 minor unit；账户不存在时以系统错误失败
     */
    int accountMinorUnit(long accountId);
}
