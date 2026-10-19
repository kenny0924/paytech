package com.paytech.ledger.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.FactApplyStatus;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.EconomicFactPayload;

/**
 * account 入账批次（AccountPosted 载荷）模板：扮演 account 为经济事实或桶移动生成的批次，序号由用例控制
 * <li>默认流为事实 OE（entityNo）+ 币种 + 分片 0；批次主键与流水主键取测试内递增的合成值，不访问数据库。</li>
 * <li>流水按追加顺序编行号；金额为主货币单位普通十进制，方向与桶为 account-api 稳定 Code。</li>
 */
public final class AccountPostingBuilder {
    /** 合成批次与流水主键的起点（大于初始化 DML 保留区间）。 */
    private static final AtomicLong SYNTHETIC_ID = new AtomicLong(7_300_000_000_000_000L);

    private final AccountPostedPayload payload = new AccountPostedPayload();

    private AccountPostingBuilder() {
    }

    /** 已应用事实的批次：主体为事实 OE，序号由调用方给出，流水为空。 */
    public static AccountPostingBuilder fact(EconomicFactPayload fact, long postingSequence) {
        AccountPostingBuilder builder = stream(AccountEntityType.OPERATING_ENTITY, fact.getEntityNo(), fact.getCurrency(),
            postingSequence);
        builder.payload.setSourceType(PostingSourceType.ECONOMIC_FACT.getCode());
        builder.payload.setFactId(fact.getFactId());
        builder.payload.setFactVersion(fact.getFactVersion());
        builder.payload.setFactType(fact.getFactType());
        builder.payload.setSupersedesFactId(fact.getSupersedesFactId());
        builder.payload.setFactApplyStatus(FactApplyStatus.APPLIED.getCode());
        builder.payload.setFinanceEntityId(fact.getFinanceEntityId());
        return builder;
    }

    /** 桶移动批次：同一 Client 应付账户 AVAILABLE → LOCKED 两条流水。 */
    public static AccountPostingBuilder movement(String entityNo, String currency, long postingSequence, String clientId,
        String amount) {
        AccountPostingBuilder builder = stream(AccountEntityType.OPERATING_ENTITY, entityNo, currency, postingSequence);
        builder.payload.setSourceType(PostingSourceType.BALANCE_MOVEMENT.getCode());
        builder.payload.setMovementId(SYNTHETIC_ID.incrementAndGet());
        builder.payload.setMovementSourceType(MovementSourceType.PAYOUT_LOCK.getCode());
        builder.payload.setMovementSourceRef("payout-" + builder.payload.getMovementId());
        return builder.entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, clientId,
                BalanceBucket.AVAILABLE, EntrySide.DEBIT, amount, null)
            .entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, clientId,
                BalanceBucket.LOCKED, EntrySide.CREDIT, amount, null);
    }

    /** 过账流头：主体、币种、分片 0、序号与入账时间。 */
    static AccountPostingBuilder stream(AccountEntityType entityType, String entityId, String currency,
        long postingSequence) {
        AccountPostingBuilder builder = new AccountPostingBuilder();
        builder.payload.setPostingId(SYNTHETIC_ID.incrementAndGet());
        builder.payload.setEntityType(entityType.getCode());
        builder.payload.setEntityId(entityId);
        builder.payload.setCurrency(currency);
        builder.payload.setShardNo(0);
        builder.payload.setPostingSequence(postingSequence);
        builder.payload.setPostedTime(Instant.now().truncatedTo(ChronoUnit.MICROS));
        builder.payload.setEntries(new ArrayList<>());
        return builder;
    }

    /** 事实后到且已被替代：SKIPPED，无流水。 */
    public AccountPostingBuilder skipped() {
        payload.setFactApplyStatus(FactApplyStatus.SKIPPED.getCode());
        payload.getEntries().clear();
        return this;
    }

    /** 覆盖序号（负例或乱序场景）。 */
    public AccountPostingBuilder postingSequence(long postingSequence) {
        payload.setPostingSequence(postingSequence);
        return this;
    }

    /** 覆盖事实版本（与 billing 不一致场景）。 */
    public AccountPostingBuilder factVersion(Long factVersion) {
        payload.setFactVersion(factVersion);
        return this;
    }

    /**
     * 追加一条流水
     *
     * @param factLineNo 对应的事实明细行号；桶移动为空
     */
    public AccountPostingBuilder entry(String accountCode, OwnerType ownerType, String ownerId, BalanceBucket bucket,
        EntrySide side, String amount, Integer factLineNo) {
        AccountPostedEntryPayload entry = new AccountPostedEntryPayload();
        entry.setEntryId(SYNTHETIC_ID.incrementAndGet());
        entry.setLineNo(payload.getEntries().size() + 1);
        entry.setAccountId(SYNTHETIC_ID.incrementAndGet());
        entry.setAccountCode(accountCode);
        entry.setOwnerType(ownerType.getCode());
        entry.setOwnerId(ownerId);
        entry.setBalanceBucket(bucket.getCode());
        entry.setEntrySide(side.getCode());
        entry.setAmount(new BigDecimal(amount));
        entry.setFactLineNo(factLineNo);
        payload.getEntries().add(entry);
        return this;
    }

    /** 把最后一条流水标记为冲回给定流水。 */
    public AccountPostingBuilder reverses(long entryId) {
        payload.getEntries().getLast().setReversesEntryId(entryId);
        return this;
    }

    /** 返回载荷。 */
    public AccountPostedPayload build() {
        return payload;
    }
}
