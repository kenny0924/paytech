package com.paytech.account.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.account.api.enums.FactApplyStatus;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.dataobject.BalanceMovement;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.domain.view.AccountPostingView;
import com.paytech.foundation.money.Money;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 入账批次到 {@link AccountPostedPayload} 的转换与 AccountPosted 事件 JSON
 * <li>JSON 字段顺序固定（同 T0 合同示例）；所有标量写成字符串（ID、序号、版本、行号为十进制，金额按币种精度补齐，时间为 UTC RFC 3339），
 * 缺省为 JSON null，符合 Foundation JSON Object Profile。</li>
 */
public final class AccountPostedPayloads {

    private AccountPostedPayloads() {
    }

    /**
     * 组装载荷
     *
     * @param view 批次、来源与全部流水
     * @return 载荷；金额按批次币种精度补齐
     */
    public static AccountPostedPayload of(AccountPostingView view) {
        AccountPosting posting = view.getPosting();
        AccountPostedPayload payload = new AccountPostedPayload();
        payload.setPostingId(posting.getId());
        payload.setEntityType(posting.getEntityType().getCode());
        payload.setEntityId(posting.getEntityId());
        payload.setCurrency(posting.getCurrency());
        payload.setShardNo(posting.getShardNo());
        payload.setPostingSequence(posting.getPostingSequence());
        payload.setSourceType(posting.getSourceType().getCode());
        payload.setPostedTime(posting.getPostedTime());
        fillFact(payload, posting, view.getAppliedFact(), view.factApplyStatus());
        fillMovement(payload, view.getMovement());
        payload.setEntries(view.getLines().stream().map(line -> entry(posting.getCurrency(), line)).toList());
        return payload;
    }

    /** 事实批次字段；桶移动批次保持为空。 */
    static void fillFact(AccountPostedPayload payload, AccountPosting posting, BalanceAppliedFact fact,
        FactApplyStatus status) {
        if (fact == null) {
            return;
        }
        payload.setFactId(posting.getFactId());
        payload.setFactVersion(posting.getFactVersion());
        payload.setFactType(fact.getFactType());
        payload.setSupersedesFactId(fact.getSupersedesFactId());
        payload.setFactApplyStatus(status.getCode());
        payload.setFinanceEntityId(fact.getFinanceEntityId());
    }

    /** 桶移动批次字段；事实批次保持为空。 */
    static void fillMovement(AccountPostedPayload payload, BalanceMovement movement) {
        if (movement == null) {
            return;
        }
        payload.setMovementId(movement.getId());
        payload.setMovementSourceType(movement.getSourceType().getCode());
        payload.setMovementSourceRef(movement.getSourceRef());
    }

    /** 一条流水载荷。 */
    static AccountPostedEntryPayload entry(String currency, AccountPostingView.Line line) {
        AccountEntry entry = line.getEntry();
        OwnerAccount account = line.getAccount();
        AccountPostedEntryPayload payload = new AccountPostedEntryPayload();
        payload.setEntryId(entry.getId());
        payload.setLineNo(entry.getLineNo());
        payload.setAccountId(account.getId());
        payload.setAccountCode(account.getAccountCode());
        payload.setOwnerType(account.getOwnerType().getCode());
        payload.setOwnerId(account.getOwnerId());
        payload.setBalanceBucket(account.getBalanceBucket().getCode());
        payload.setEntrySide(entry.getEntrySide().getCode());
        payload.setAmount(scaled(currency, entry.getAmount()));
        payload.setFactLineNo(entry.getFactLineNo());
        payload.setReversesEntryId(entry.getReversesEntryId());
        return payload;
    }

    /**
     * AccountPosted Schema v1 Payload：{@link AccountPostedPayload} 的全部字段，固定顺序
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(AccountPostedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("postingId", text(payload.getPostingId()));
        node.put("entityType", payload.getEntityType());
        node.put("entityId", payload.getEntityId());
        node.put("currency", payload.getCurrency());
        node.put("shardNo", text(payload.getShardNo()));
        node.put("postingSequence", text(payload.getPostingSequence()));
        node.put("sourceType", payload.getSourceType());
        node.put("postedTime", text(payload.getPostedTime()));
        node.put("factId", text(payload.getFactId()));
        node.put("factVersion", text(payload.getFactVersion()));
        node.put("factType", payload.getFactType());
        node.put("supersedesFactId", text(payload.getSupersedesFactId()));
        node.put("factApplyStatus", payload.getFactApplyStatus());
        node.put("financeEntityId", text(payload.getFinanceEntityId()));
        node.put("movementId", text(payload.getMovementId()));
        node.put("movementSourceType", payload.getMovementSourceType());
        node.put("movementSourceRef", payload.getMovementSourceRef());
        ArrayNode entries = node.putArray("entries");
        payload.getEntries().forEach(entry -> entries.add(json(entry)));
        return node;
    }

    /** 一条流水 JSON。 */
    static ObjectNode json(AccountPostedEntryPayload entry) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("entryId", text(entry.getEntryId()));
        node.put("lineNo", text(entry.getLineNo()));
        node.put("accountId", text(entry.getAccountId()));
        node.put("accountCode", entry.getAccountCode());
        node.put("ownerType", entry.getOwnerType());
        node.put("ownerId", entry.getOwnerId());
        node.put("balanceBucket", entry.getBalanceBucket());
        node.put("entrySide", entry.getEntrySide());
        node.put("amount", text(entry.getAmount()));
        node.put("factLineNo", text(entry.getFactLineNo()));
        node.put("reversesEntryId", text(entry.getReversesEntryId()));
        return node;
    }

    /** 按币种精度补齐的金额。 */
    static BigDecimal scaled(String currency, BigDecimal amount) {
        return new BigDecimal(Money.of(currency, amount).toAmountString());
    }

    /** 十进制或时间文本；为空时为空。 */
    static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value == null ? null : value.toString();
    }
}
