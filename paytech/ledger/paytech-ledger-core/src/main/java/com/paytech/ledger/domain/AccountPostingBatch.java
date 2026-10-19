package com.paytech.ledger.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.FactApplyStatus;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.messaging.api.AggregateKey;

import lombok.Getter;

/**
 * 一个已校验的 account 入账批次（AccountPosted 载荷，或追平时经 account-api 读取的同构批次）
 * <li>消费 Handler 与追平命令共用：校验载荷完整、解析稳定 Code，并给出过账流的聚合身份与是否需要过账。</li>
 * <li>只做纯内存校验，不访问数据库或外部服务；非法内容抛 {@link IllegalArgumentException}（消费入口据此进入死信）。</li>
 */
@Getter
public final class AccountPostingBatch {
    /** account 入账批次指令的来源系统代码（t_accounting_instruction.source_system）。 */
    public static final String SOURCE_SYSTEM = "account";

    /** 原始载荷。 */
    private final AccountPostedPayload payload;

    /** 过账流的聚合身份（ACCOUNT_POSTING + {OE|FE}.{entityId}.{currency}.{shardNo}），即消费进度键。 */
    private final AggregateKey aggregateKey;

    /** 批次来源。 */
    private final PostingSourceType sourceType;

    /**
     * 事实应用结果
     * <li>桶移动批次为空。</li>
     */
    private final FactApplyStatus factApplyStatus;

    /** 由 {@link #of(AccountPostedPayload)} 构造。 */
    private AccountPostingBatch(AccountPostedPayload payload, AggregateKey aggregateKey, PostingSourceType sourceType,
        FactApplyStatus factApplyStatus) {
        this.payload = payload;
        this.aggregateKey = aggregateKey;
        this.sourceType = sourceType;
        this.factApplyStatus = factApplyStatus;
    }

    /**
     * 校验并包装批次
     *
     * @param payload AccountPosted 载荷
     * @return 已校验的批次
     * @throws IllegalArgumentException 必填字段缺失、Code 未知、序号非正或流水非法
     */
    public static AccountPostingBatch of(AccountPostedPayload payload) {
        require(payload, "payload");
        require(payload.getPostingId(), "postingId");
        require(payload.getPostingSequence(), "postingSequence");
        require(payload.getShardNo(), "shardNo");
        if (payload.getPostingSequence() < 1) {
            throw new IllegalArgumentException("postingSequence must be positive: " + payload.getPostingId());
        }
        AccountEntityType entityType = parse(AccountEntityType.class, payload.getEntityType(), "entityType");
        AggregateKey key = AggregateKey.of(AccountPostedPayload.AGGREGATE_TYPE,
            AccountPostedPayload.aggregateId(entityType, payload.getEntityId(), payload.getCurrency(), payload.getShardNo()));
        PostingSourceType sourceType = parse(PostingSourceType.class, payload.getSourceType(), "sourceType");
        require(payload.getEntries(), "entries");
        if (sourceType == PostingSourceType.BALANCE_MOVEMENT) {
            return new AccountPostingBatch(payload, key, sourceType, null);
        }
        require(payload.getFactId(), "factId");
        require(payload.getFactVersion(), "factVersion");
        require(payload.getFactType(), "factType");
        FactApplyStatus applyStatus = parse(FactApplyStatus.class, payload.getFactApplyStatus(), "factApplyStatus");
        payload.getEntries().forEach(AccountPostingBatch::checkEntry);
        return new AccountPostingBatch(payload, key, sourceType, applyStatus);
    }

    /** 事实批次的每条流水：主键、行号、科目、Owner、方向与正金额必须存在。 */
    static void checkEntry(AccountPostedEntryPayload entry) {
        require(entry, "entry");
        require(entry.getEntryId(), "entryId");
        require(entry.getLineNo(), "lineNo");
        require(entry.getAccountCode(), "accountCode");
        require(entry.getOwnerId(), "ownerId");
        parse(EntrySide.class, entry.getEntrySide(), "entrySide");
        BigDecimal amount = entry.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("entry amount must be positive: " + entry.getEntryId());
        }
    }

    /** 批次在过账流内的序号，即事件聚合版本。 */
    public long sequence() {
        return payload.getPostingSequence();
    }

    /** 批次主键的十进制文本，即 ACCOUNT_POSTING 指令的来源事件键。 */
    public String postingKey() {
        return String.valueOf(payload.getPostingId());
    }

    /**
     * 是否需要过账（读取事实并生成指令）
     * <li>APPLIED 事实批次需要；SKIPPED 批次（被替代事实后到）在带流水（所替代事实的冲回行）或有被替代事实时需要，否则只推进进度。</li>
     * <li>桶移动批次只推进进度。</li>
     */
    public boolean requiresPosting() {
        if (sourceType != PostingSourceType.ECONOMIC_FACT) {
            return false;
        }
        return appliesRules() || !payload.getEntries().isEmpty() || payload.getSupersedesFactId() != null;
    }

    /** 是否对事实本身应用规则包：只有 APPLIED 批次；SKIPPED 批次只记流水行与被替代事实规则行的反向行。 */
    public boolean appliesRules() {
        return factApplyStatus == FactApplyStatus.APPLIED;
    }

    /** 批次的全部流水，按行号升序。 */
    public List<AccountPostedEntryPayload> entries() {
        return payload.getEntries().stream()
            .sorted(Comparator.comparing(AccountPostedEntryPayload::getLineNo)).toList();
    }

    /** 必填值缺失时拒绝。 */
    static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("AccountPosted " + field + " is missing");
        }
    }

    /** 按稳定 Code 解析固定枚举，未知 Code 拒绝。 */
    static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String code, String field) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("AccountPosted " + field + " is unknown: " + code);
    }
}
