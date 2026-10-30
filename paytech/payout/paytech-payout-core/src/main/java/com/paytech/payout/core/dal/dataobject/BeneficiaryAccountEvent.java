package com.paytech.payout.core.dal.dataobject;

import java.time.Instant;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryEventAction;

import lombok.Getter;
import lombok.Setter;

/**
 * t_beneficiary_account_event（收款账户事件）
 * <li>不可变事件链：每次状态变化与原值查看在同一本地事务追加；不含任何原值或 Token。</li>
 */
@Getter
@Setter
public class BeneficiaryAccountEvent {
    /**
     * 事件ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 收款账户ID
     */
    private Long beneficiaryAccountId;

    /**
     * 事件序号
     * <li>账户内从 1 严格连续，在 Owner 锁内分配，唯一索引兜底。</li>
     */
    private Integer eventNo;

    /**
     * 变更前状态
     * <li>首条（SUBMIT）为空。</li>
     */
    private BeneficiaryAccountStatus fromStatus;

    /**
     * 变更后状态
     * <li>不改状态的动作与 fromStatus 相同。</li>
     */
    private BeneficiaryAccountStatus toStatus;

    /**
     * 动作
     */
    private BeneficiaryEventAction action;

    /**
     * 原因代码
     * <li>拒绝 / 停用 / 关闭原因或 Reveal 原因；无原因为空。</li>
     */
    private String reasonCode;

    /**
     * 操作人类型
     */
    private BeneficiaryActorType actorType;

    /**
     * 操作人标识
     * <li>SYSTEM 为空。</li>
     */
    private Long actorId;

    /**
     * 来源键
     * <li>触发命令的 Request ID；用于审计关联。</li>
     */
    private String sourceKey;

    /**
     * 变更后账户版本
     */
    private Long accountVersionAfter;

    /**
     * 发生时间
     */
    private Instant occurredTime;

    /**
     * 创建时间
     */
    private Instant createTime;
}
