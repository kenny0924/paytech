package com.paytech.payout.api.beneficiary.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 收款账户视图（P1—P9、P11）
 * <li>不含任何原值或 Vault Token：户名只给脱敏值，账号只给末四位。</li>
 * <li>usable 由服务端按 status = VERIFIED 且当前时间不早于 coolingUntil 实时判定，不依赖定时任务。</li>
 */
@Getter
@Setter
public class BeneficiaryAccountResponseBody {
    /** 收款账户 ID */
    private String beneficiaryAccountId;

    /** Owner 类型：CLIENT、LOCAL_PARTNER */
    private String ownerType;

    /** Owner 标识 */
    private String ownerId;

    /** Owner 内可读编号，例如 BA-0001 */
    private String accountCode;

    /** 收款方式：LOCAL、SWIFT */
    private String railType;

    /** 开户国家（ISO 3166-1 alpha-2） */
    private String bankCountry;

    /** 币种（ISO 4217） */
    private String currency;

    /** 银行显示名称 */
    private String bankName;

    /**
     * 银行代码或 SWIFT BIC
     * <li>只用 IBAN 的国家为空。</li>
     */
    private String bankCode;

    /** 户名脱敏值 */
    private String holderNameMasked;

    /** 账号或 IBAN 末四位 */
    private String accountLast4;

    /** 冻结内容摘要；Maker / Checker 审阅时原样回传 */
    private String contentHash;

    /** 状态：PENDING_REVIEW、VERIFIED、REJECTED、SUSPENDED、CLOSED */
    private String status;

    /**
     * 最近一次拒绝、停用或关闭的原因代码
     * <li>无原因时为空。</li>
     */
    private String reasonCode;

    /** 提交人类型：CLIENT_MEMBER、OPERATIONS */
    private String submittedByType;

    /** 提交人账号 ID */
    private String submittedBy;

    /** Maker 账号 ID；尚无意见时为空 */
    private String reviewMakerId;

    /** Maker 意见：APPROVE、REJECT；尚无意见时为空 */
    private String reviewMakerDecision;

    /** Checker 账号 ID；尚未复核时为空 */
    private String checkerId;

    /** Checker 决定：APPROVED、REJECTED；尚未复核时为空 */
    private String checkerDecision;

    /** 复核决定的 UTC 时间；尚未复核时为空 */
    private Instant decisionTime;

    /**
     * 冷却结束的 UTC 时间
     * <li>VERIFIED 时 = 决定时间 + 配置冷却小时；其他状态为空。</li>
     */
    private Instant coolingUntil;

    /** 当前是否可用于出款（VERIFIED 且冷却结束） */
    private boolean usable;

    /** 是否为同 Owner、同币种的默认账户 */
    private boolean defaultAccount;

    /**
     * 给 Owner 的说明
     * <li>拒绝时运营填写的可选文字（Maker 建议拒绝时的说明，或 Checker 驳回时的说明）；没有时为空。</li>
     */
    private String reasonNote;

    /**
     * Maker 核验意见备注
     * <li>只对 Operations BFF 返回（O2 Maker 意见下方），其他调用方为空。</li>
     */
    private String reviewMakerNote;

    /**
     * 以同一账号登记在其他 Owner 名下的未终结账户
     * <li>只对 Operations BFF 返回（核验页共用提示），最多 5 条，按创建时间升序；没有共用时为空数组，其他调用方为空。</li>
     */
    private List<SharedOwnerItem> sharedOwners;

    /** 乐观锁版本；写命令的 expectedVersion 取此值 */
    private String version;

    /** 创建 UTC 时间 */
    private Instant createTime;

    /** 最近更新 UTC 时间 */
    private Instant updateTime;

    /**
     * 事件时间线
     * <li>只在详情（P3）返回，按事件序号升序；列表与写命令响应为空。</li>
     */
    private List<BeneficiaryAccountEventItem> events;

    /** 共用同一账号的其他 Owner 账户。 */
    @Getter
    @Setter
    public static class SharedOwnerItem {
        /** Owner 类型：CLIENT、LOCAL_PARTNER */
        private String ownerType;

        /** Owner 标识 */
        private String ownerId;

        /** 该 Owner 名下的账户编号 */
        private String accountCode;

        /** 该账户状态（未终结：PENDING_REVIEW、VERIFIED、SUSPENDED） */
        private String status;
    }

    /** 一条收款账户事件。 */
    @Getter
    @Setter
    public static class BeneficiaryAccountEventItem {
        /** 账户内事件序号，从 1 连续 */
        private int eventNo;

        /** 变更前状态；首条为空 */
        private String fromStatus;

        /** 变更后状态 */
        private String toStatus;

        /** 动作：SUBMIT、MAKER_REVIEW、CHECKER_DECIDE、SET_DEFAULT、UNSET_DEFAULT、SUSPEND、RESUME、CLOSE、REVEAL */
        private String action;

        /** 原因代码；无原因时为空 */
        private String reasonCode;

        /** 操作人类型：CLIENT_MEMBER、OPERATIONS、SYSTEM */
        private String actorType;

        /** 操作人账号 ID；SYSTEM 为空 */
        private String actorId;

        /** 发生 UTC 时间 */
        private Instant occurredTime;
    }
}
