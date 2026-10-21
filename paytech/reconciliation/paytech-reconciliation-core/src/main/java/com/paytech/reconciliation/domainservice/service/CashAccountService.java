package com.paytech.reconciliation.domainservice.service;

import com.paytech.reconciliation.core.dal.dataobject.CashAccount;

/**
 * 平台银行账户的新建、关闭申请与复核保存（UC-R02，technique D-03）
 * <li>事务传播为 REQUIRED；调用方已完成 Vault Protect、提交人 / 复核人与摘要校验，本服务只做状态迁移与保存。</li>
 */
public interface CashAccountService {

    /**
     * 保存新建的 PENDING_VERIFICATION 银行账户
     *
     * @param account 已准备主键、令牌、末四位与内容摘要的新记录
     */
    void create(CashAccount account);

    /**
     * ACTIVE 账户记关闭申请（pendingAction = CLOSE），提交人与内容摘要更新为本次申请
     *
     * @param account 已读取的 ACTIVE 账户
     * @param makerId 申请人
     * @param contentHash 关闭申请的内容摘要
     */
    void requestClosure(CashAccount account, Long makerId, String contentHash);

    /**
     * 复核通过：新建 → ACTIVE；关闭申请 → CLOSED
     *
     * @param account 已读取的待复核账户
     * @param checkerId 复核人
     */
    void approve(CashAccount account, Long checkerId);

    /**
     * 复核拒绝：新建 → CLOSED（决定 I-04）；关闭申请 → 清空申请，仍 ACTIVE
     *
     * @param account 已读取的待复核账户
     * @param checkerId 复核人
     */
    void reject(CashAccount account, Long checkerId);
}
