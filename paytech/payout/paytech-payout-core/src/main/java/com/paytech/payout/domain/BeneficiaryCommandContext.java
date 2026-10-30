package com.paytech.payout.domain;

import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对一个已验证账户执行本地保存所需的上下文：账户、操作人、原因与来源键
 * <li>Processor 在 Owner 锁内完成归属、状态、版本与四眼校验后一次构造；Service 不重复校验。</li>
 */
@Getter
@AllArgsConstructor
public class BeneficiaryCommandContext {
    /** 已读取并校验的账户；Repository 条件更新成功后原地同步 */
    private final BeneficiaryAccount account;

    /** 操作人类型 */
    private final BeneficiaryActorType actorType;

    /** 操作人账号 */
    private final Long actorId;

    /** 原因代码，可为空 */
    private final String reasonCode;

    /** 来源键（Request ID） */
    private final String sourceKey;
}
