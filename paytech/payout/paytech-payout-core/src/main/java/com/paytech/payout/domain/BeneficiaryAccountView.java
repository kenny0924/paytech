package com.paytech.payout.domain;

import java.util.List;

import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收款账户查询与命令结果：账户、实时可用判定，以及按需组合的共用提示与事件（多表组合）
 * <li>sharedAccounts 只在运营视图给出（同时决定是否输出 Maker 备注），其他为 null；events 只在详情给出，其他为 null。</li>
 */
@Getter
@AllArgsConstructor
public class BeneficiaryAccountView {
    /** 运营视图共用提示最多列出的其他 Owner 账户数。 */
    public static final int SHARED_ACCOUNT_LIMIT = 5;

    /** 账户 */
    private final BeneficiaryAccount account;

    /** 构造视图时是否可用（VERIFIED 且冷却结束） */
    private final boolean usable;

    /** 其他 Owner 以同一账号持有的未终结账户（最多 SHARED_ACCOUNT_LIMIT 条）；非运营视图为 null */
    private final List<BeneficiaryAccount> sharedAccounts;

    /**
     * 是否运营视图
     *
     * @return 给出了共用账户列表时为真
     */
    public boolean isOperationsView() {
        return sharedAccounts != null;
    }

    /** 事件时间线；非详情为 null */
    private final List<BeneficiaryAccountEvent> events;
}
