package com.paytech.account.api.response;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/**
 * 一个过账批次及其全部流水
 * <li>字段与 AccountPosted 载荷（{@code AccountPostedPayload}）一一对应，ledger 追平与消费按同一逻辑过账；ID、版本、序号与行号为十进制字符串，
 * 金额为 Money。唯一例外是流水行的 accountNo：只用于显示，不进入事件载荷。</li>
 * <li>事实批次填 fact* 与 financeEntityId，桶移动批次填 movement*，另一组为空。</li>
 */
@Getter
@Setter
public class AccountPostingResponseBody {
    /** 批次主键。 */
    private String postingId;

    /** 主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    private String entityType;

    /** 主体标识。 */
    private String entityId;

    /** 批次币种。 */
    private String currency;

    /** 流分片号。 */
    private String shardNo;

    /** 流内批次序号。 */
    private String postingSequence;

    /** 批次来源：ECONOMIC_FACT 或 BALANCE_MOVEMENT。 */
    private String sourceType;

    /** 批次生成的 UTC 时间。 */
    private Instant postedTime;

    /** AccountPosted 收到 Broker 确认的 UTC 时间；尚未发布为空。 */
    private Instant publishedTime;

    /** 事实批次：计费事实主键。 */
    private String factId;

    /** 事实批次：计费事实版本。 */
    private String factVersion;

    /** 事实批次：计费事实类型代码。 */
    private String factType;

    /** 事实批次：本事实替代的前一事实；首次登记为空。 */
    private String supersedesFactId;

    /** 事实批次：应用结果 APPLIED 或 SKIPPED。 */
    private String factApplyStatus;

    /** 事实批次：平台服务费收取方 Finance Entity；无合作方为空。 */
    private String financeEntityId;

    /** 桶移动批次：移动记录主键。 */
    private String movementId;

    /** 桶移动批次：移动来源类型。 */
    private String movementSourceType;

    /** 桶移动批次：来源业务引用。 */
    private String movementSourceRef;

    /** 按行号升序的全部流水；无 Owner 影响或 SKIPPED 的事实批次为空列表。 */
    private List<Entry> entries;

    /** 一条明细流水。 */
    @Getter
    @Setter
    public static class Entry {
        /** 流水主键。 */
        private String entryId;

        /** 批次内行号。 */
        private String lineNo;

        /** Owner 账户主键。 */
        private String accountId;

        /** Owner 账户的可读账户号，只用于显示；AccountPosted 载荷没有该字段。 */
        private String accountNo;

        /** 科目代码。 */
        private String accountCode;

        /** 核算对象类型：CLIENT、LOCAL_PARTNER 或 CASH_ACCOUNT。 */
        private String ownerType;

        /** 核算对象标识。 */
        private String ownerId;

        /** 余额桶：PENDING、AVAILABLE、LOCKED 或 NONE。 */
        private String balanceBucket;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        private String entrySide;

        /** 正金额，币种同批次。 */
        private Money amount;

        /** 来源事实明细行号；桶移动批次为空。 */
        private String factLineNo;

        /** 被冲回的原流水主键；非冲回行为空。 */
        private String reversesEntryId;
    }
}
