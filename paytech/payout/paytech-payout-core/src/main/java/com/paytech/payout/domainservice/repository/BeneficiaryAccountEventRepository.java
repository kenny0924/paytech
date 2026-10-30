package com.paytech.payout.domainservice.repository;

import java.util.List;

import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;

/** t_beneficiary_account_event（收款账户事件）仓储：只追加与读取，不开事务。 */
public interface BeneficiaryAccountEventRepository {
    /**
     * 追加一条事件；同账户事件序号唯一由 uk_beneficiary_event_no 兜底。
     *
     * @param record 已准备主键与序号的事件
     */
    void insert(BeneficiaryAccountEvent record);

    /**
     * 账户下一条事件的序号（当前最大序号 + 1）；调用方须持有该账户 Owner 的资源锁。
     *
     * @param beneficiaryAccountId 账户主键
     * @return 序号，从 1 开始
     */
    int nextEventNo(Long beneficiaryAccountId);

    /**
     * 账户的全部事件。
     *
     * @param beneficiaryAccountId 账户主键
     * @return 按序号升序
     */
    List<BeneficiaryAccountEvent> listByAccount(Long beneficiaryAccountId);

    /**
     * 账户内的一条事件。
     *
     * @param beneficiaryAccountId 账户主键
     * @param eventNo 序号
     * @return 事件；不存在为 null
     */
    BeneficiaryAccountEvent findByEventNo(Long beneficiaryAccountId, Integer eventNo);
}
