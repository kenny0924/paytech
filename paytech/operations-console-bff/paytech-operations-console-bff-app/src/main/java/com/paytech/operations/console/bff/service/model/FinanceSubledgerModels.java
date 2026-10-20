package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/**
 * 明细账查询页面模型：Owner 账户、账户流水与过账批次
 * <li>字段与 account 合同一一对应，由 Client 适配器严格转换；Owner 新增字段即视为合同漂移。</li>
 * <li>金额为 Foundation Money（币种 + 主单位字符串，已按币种精度补齐）；ID、版本、序号与行号为十进制字符串。</li>
 * <li>Query 校验正则与 account-api 枚举 Code 一致（注解需要编译期常量，由 auto 测试逐值核对）。</li>
 *
 * @author Claude
 */
public final class FinanceSubledgerModels {
    /** 明细账主体类型，与 account-api {@code AccountEntityType} 一致。 */
    public static final String ENTITY_TYPE_PATTERN = "OPERATING_ENTITY|FINANCE_ENTITY";

    /** 明细账核算对象类型，与 account-api {@code OwnerType} 一致。 */
    public static final String OWNER_TYPE_PATTERN = "CLIENT|LOCAL_PARTNER|CASH_ACCOUNT";

    /** 余额桶，与 account-api {@code BalanceBucket} 一致。 */
    public static final String BALANCE_BUCKET_PATTERN = "PENDING|AVAILABLE|LOCKED|NONE";

    /** 只承载类型，禁止创建实例。 */
    private FinanceSubledgerModels() {
    }

    /** 一个 Owner 账户及其当前余额。 */
    @Getter
    @Setter
    public static class OwnerAccountResponseBody {
        /** Owner 账户主键（account 账户，不是 ledger 账户）。 */
        private String accountId;

        /** 可读账户号：{主体段}-{币种}-{科目编号}-S{序号}，开户后不变。 */
        private String accountNo;

        /** 科目代码。 */
        private String accountCode;

        /** 核算对象类型：CLIENT、LOCAL_PARTNER 或 CASH_ACCOUNT。 */
        private String ownerType;

        /** 核算对象标识。 */
        private String ownerId;

        /** 余额桶：PENDING、AVAILABLE、LOCKED 或 NONE。 */
        private String balanceBucket;

        /** 正常余额方向：DEBIT 或 CREDIT。 */
        private String normalSide;

        /** 账户状态：ACTIVE、FROZEN 或 CLOSED。 */
        private String accountStatus;

        /** 全部流水借方合计。 */
        private Money debitTotal;

        /** 全部流水贷方合计。 */
        private Money creditTotal;

        /**
         * 净余额
         * <li>按正常方向计算，允许为负。</li>
         */
        private Money netBalance;

        /** 余额乐观锁版本。 */
        private String balanceVersion;
    }

    /** Owner 账户的一条明细流水。 */
    @Getter
    @Setter
    public static class OwnerAccountEntryResponseBody {
        /** 流水主键。 */
        private String entryId;

        /** 所属过账批次主键。 */
        private String postingId;

        /** 批次在其过账流内的序号。 */
        private String postingSequence;

        /** 批次来源：ECONOMIC_FACT 或 BALANCE_MOVEMENT。 */
        private String sourceType;

        /** 事实批次的经济事实主键；桶移动批次为空。 */
        private String factId;

        /** 事实批次的经济事实版本；桶移动批次为空。 */
        private String factVersion;

        /** 事实批次的经济事实类型代码；桶移动批次为空。 */
        private String factType;

        /** 桶移动批次的移动来源类型；事实批次为空。 */
        private String movementSourceType;

        /** 批次内行号。 */
        private String lineNo;

        /** 来源事实明细行号；桶移动批次为空。 */
        private String factLineNo;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        private String entrySide;

        /** 正金额。 */
        private Money amount;

        /** 被冲回的原流水主键；非冲回行为空。 */
        private String reversesEntryId;

        /** 批次生成的 UTC 时间。 */
        private Instant postedTime;
    }

    /** 一个过账批次及其全部流水；事实批次填 fact* 与 financeEntityId，桶移动批次填 movement*。 */
    @Getter
    @Setter
    public static class AccountPostingResponseBody {
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

        /** 事实批次：经济事实主键。 */
        private String factId;

        /** 事实批次：经济事实版本。 */
        private String factVersion;

        /** 事实批次：经济事实类型代码。 */
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

        /** 按行号升序的全部流水；SKIPPED 或无 Owner 影响的事实批次为空列表。 */
        private List<Entry> entries;
    }

    /** 过账批次中的一条流水。 */
    @Getter
    @Setter
    public static class Entry {
        /** 流水主键。 */
        private String entryId;

        /** 批次内行号。 */
        private String lineNo;

        /** Owner 账户主键。 */
        private String accountId;

        /** Owner 账户的可读账户号。 */
        private String accountNo;

        /** 科目代码。 */
        private String accountCode;

        /** 核算对象类型。 */
        private String ownerType;

        /** 核算对象标识。 */
        private String ownerId;

        /** 余额桶。 */
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
