package com.paytech.reconciliation.domainservice.service;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;

/**
 * 预期银行流水的生成（设计 F2；B 线建立，C 线扩展）
 * <li>在调用方事务内执行；按 uk(movement_type, source_id) 每个来源只生成一条。</li>
 * <li><b>扩展点（C 线）：</b>预期银行流水生成后需要对同一银行账户的未匹配流水重新出建议（「钱比账单先到」），C 线在本服务的生成方法里
 * 同一事务登记 BANK_MATCH_SUGGEST 本地任务；B 线的调用方不需要改动。</li>
 */
public interface ExpectedBankMovementService {

    /**
     * 渠道账单对平后生成 CHANNEL_SETTLEMENT 预期银行流水
     * <li>银行账户取清算账户的到账银行账户，方向取账单净额方向，金额为净额，预期日为结算日，状态 OPEN。</li>
     * <li>调用方保证净额方向不是 NONE 且到账银行账户已配置（对平门禁）。</li>
     *
     * @param statement 已对平的渠道账单
     * @param clearingAccount 所属清算账户
     * @return 生成的预期银行流水
     */
    ExpectedBankMovement createChannelSettlement(ChannelStatement statement, ClearingAccount clearingAccount);
}
