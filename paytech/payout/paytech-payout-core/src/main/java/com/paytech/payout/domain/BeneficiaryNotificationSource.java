package com.paytech.payout.domain;

import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 通知补偿任务从本库重建通知所需的事实：账户与触发事件。 */
@Getter
@AllArgsConstructor
public class BeneficiaryNotificationSource {
    /** 账户（当前状态） */
    private final BeneficiaryAccount account;

    /** 触发通知的事件 */
    private final BeneficiaryAccountEvent event;
}
