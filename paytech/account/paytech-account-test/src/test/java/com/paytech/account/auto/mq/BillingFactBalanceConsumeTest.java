package com.paytech.account.auto.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.FactApplyStatus;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.MaturityStatus;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.builder.BillingFactBuilder;
import com.paytech.account.test.probe.AccountPostedProbe;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.BillingFactPayload;

/**
 * account 消费 BillingFactRecorded 入账明细账：测试扮演 billing 向真实本机 RabbitMQ 的 billing.events 发布信封，经 account.billing 分区队列、
 * paytech-messaging 幂等拦截器与 BillingFactRecordedBalanceHandler 在 account 本地事务内写批次、流水、余额与 AccountPosted 补偿事件；
 * AccountPosted 由 {@link AccountPostedProbe} 在 account.events 上观察。
 * <p>
 * 覆盖：交易事实按付方 / 收方映射（经营主体、渠道、Finance Entity 只在 ledger）；billing 自动登记的 PAYMENT 行目录（渠道付本金与手续费，有 / 无合作方，
 * 渠道付分润拒绝）；重复投递只入账一次；不同事实按到达顺序分配连续序号；替代后到（冲回行 reverses_entry_id）与
 * 替代先到（SKIPPED 0 流水批次）；人工调整按付方 / 收方映射；成本调整 0 流水批次；CLOSED 账户照常入账；到期时间落库；映射非法进死信。
 *
 * @see com.paytech.account.ext.mq.BillingFactRecordedBalanceHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 */
class BillingFactBalanceConsumeTest extends AbstractBalanceTest {
    /** AccountPosted Payload 字段顺序（T0 合同示例）。 */
    private static final List<String> PAYLOAD_FIELDS = List.of("postingId", "entityType", "entityId", "currency", "shardNo",
        "postingSequence", "sourceType", "postedTime", "factId", "factVersion", "factType", "supersedesFactId",
        "factApplyStatus", "financeEntityId", "movementId", "movementSourceType", "movementSourceRef", "entries");

    /**
     * 交易事实：本金（经营主体 → Client）记 Client 应付 PENDING 贷方、合作方分成（经营主体 → Partner）记 Partner 应付 PENDING 贷方；
     * 渠道成本（经营主体 → 渠道）与平台服务费（经营主体 → Finance Entity）不进明细账。
     */
    @Test
    void test_paymentFactPostsOwnerLines() {
        BillingFactPayload fact = payment("100").partner(PARTNER_ID, FINANCE_ENTITY_ID)
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CLIENT, "94")
            .component(FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CHANNEL, "2")
            .component(FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.OPERATING_ENTITY,
                FactPartyRole.FINANCE_ENTITY, "0.5")
            .component(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY,
                FactPartyRole.LOCAL_PARTNER, "1").build();

        consumed(fact);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "94", "94");
        assertBalance(OwnerAccountType.PARTNER_PAYABLE, PARTNER, BalanceBucket.PENDING, "0", "1", "1");
        Map<String, Object> posting = factPosting(fact.getFactId());
        List<Map<String, Object>> entries = factEntries(fact.getFactId());
        assertAll(() -> assertEquals(2, testMapper.selectAccounts().size(), "只开立 Owner 账户"),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(fact.getFactId())),
            () -> assertEquals(1L, ((Number)posting.get("posting_sequence")).longValue()),
            () -> assertEquals(2, ((Number)posting.get("entry_count")).intValue()),
            () -> assertEquals(List.of(1, 4), entries.stream().map(row -> ((Number)row.get("fact_line_no")).intValue()).toList()));

        AccountPostedProbe.Posted event = posted.await(1).getFirst();
        AccountPostedPayload payload = event.payload();
        List<String> names = new ArrayList<>();
        event.json().propertyNames().forEach(names::add);
        assertAll(() -> assertEquals(PAYLOAD_FIELDS, names),
            () -> event.json().properties().stream().filter(entry -> !"entries".equals(entry.getKey()))
                .forEach(entry -> assertTrue(entry.getValue().isString() || entry.getValue().isNull(), entry.getKey())),
            () -> assertEquals(STREAM, event.event().aggregateId()),
            () -> assertEquals(AccountEntityType.OPERATING_ENTITY.getCode(), payload.getEntityType()),
            () -> assertEquals(PostingSourceType.ECONOMIC_FACT.getCode(), payload.getSourceType()),
            () -> assertEquals(FactApplyStatus.APPLIED.getCode(), payload.getFactApplyStatus()),
            () -> assertEquals(BillingFactType.PAYMENT.getCode(), payload.getFactType()),
            () -> assertEquals(FINANCE_ENTITY_ID, payload.getFinanceEntityId()),
            () -> assertNull(payload.getMovementId()),
            () -> assertEquals("94.00", event.json().path("entries").get(0).path("amount").asString()),
            () -> assertEntry(payload.getEntries().get(0), OwnerAccountType.CLIENT_PAYABLE, CLIENT, EntrySide.CREDIT, 1),
            () -> assertEntry(payload.getEntries().get(1), OwnerAccountType.PARTNER_PAYABLE, PARTNER, EntrySide.CREDIT, 4));
    }

    /**
     * billing 自动登记的 PAYMENT 行目录（有合作方）：渠道付本金 90 记 Client 应付 PENDING 贷方、经营主体付分润 8 记 Partner 应付 PENDING 贷方；
     * 渠道付的手续费、经营主体付的平台服务费与渠道成本不产生流水，Partner 不因服务费被借记；带到期时间，到期状态 PENDING。
     */
    @Test
    void test_autoPaymentFactChannelPaysPrincipalAndFee() {
        BillingFactPayload fact = payment("100").partner(PARTNER_ID, FINANCE_ENTITY_ID).paymentLines("10", "8", "2", "0.5")
            .balanceAvailableTime(Instant.now().plus(2, ChronoUnit.DAYS)).build();

        consumed(fact);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "90", "90");
        assertBalance(OwnerAccountType.PARTNER_PAYABLE, PARTNER, BalanceBucket.PENDING, "0", "8", "8");
        List<Map<String, Object>> entries = factEntries(fact.getFactId());
        assertAll(() -> assertEquals(5, fact.getLines().size()),
            () -> assertEquals(2, testMapper.selectAccounts().size(), "只开立 Owner 账户"),
            () -> assertEquals(List.of(2, 3), entries.stream().map(row -> ((Number)row.get("fact_line_no")).intValue()).toList()),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(fact.getFactId())),
            () -> assertEquals(MaturityStatus.PENDING.getCode(),
                testMapper.selectAppliedFact(fact.getFactId()).get("maturity_status")));
        AccountPostedPayload payload = posted.await(1).getFirst().payload();
        assertAll(() -> assertEquals(2, payload.getEntries().size()),
            () -> assertEntry(payload.getEntries().get(0), OwnerAccountType.CLIENT_PAYABLE, CLIENT, EntrySide.CREDIT, 2),
            () -> assertEntry(payload.getEntries().get(1), OwnerAccountType.PARTNER_PAYABLE, PARTNER, EntrySide.CREDIT, 3));
    }

    /** billing 自动登记的 PAYMENT 行目录（无合作方，手续费、本金、渠道成本三行）：只有 Client 本金一条流水，到期状态 PENDING。 */
    @Test
    void test_autoPaymentFactWithoutPartner() {
        BillingFactPayload fact = payment("100").paymentLines("10", "0", "0", "0.5")
            .balanceAvailableTime(Instant.now().plus(2, ChronoUnit.DAYS)).build();

        consumed(fact);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "90", "90");
        assertAll(() -> assertEquals(3, fact.getLines().size()),
            () -> assertEquals(1, testMapper.selectAccounts().size(), "只开立 Client 账户"),
            () -> assertEquals(1, factEntries(fact.getFactId()).size()),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(fact.getFactId())),
            () -> assertEquals(MaturityStatus.PENDING.getCode(),
                testMapper.selectAppliedFact(fact.getFactId()).get("maturity_status")));
        posted.await(1);
    }

    /** 渠道只允许付本金与手续费、收渠道成本：渠道作为分润付方不在白名单，整条 PAYMENT 事实拒绝进入死信。 */
    @Test
    void test_channelPaidPartnerShareRejectedToDeadLetter() {
        BillingFactPayload fact = payment("100").partner(PARTNER_ID, FINANCE_ENTITY_ID).paymentLines("10", "0", "0", "0")
            .line(FactComponentType.PARTNER_SHARE, FactPartyRole.CHANNEL, String.valueOf(CHANNEL_ACCOUNT_ID),
                FactPartyRole.LOCAL_PARTNER, PARTNER, FactLineDirection.CHARGE, "8").build();

        billing.publish(fact);

        await().atMost(CONSUME_WAIT).until(() -> billing.deadLetters() == 1);
        assertRejected(fact);
    }

    /** 同一事实（同一 eventId）重复投递：消费登记一次、只有一个批次与一次 AccountPosted。 */
    @Test
    void test_duplicateDeliveryPostedOnce() {
        BillingFactPayload fact = payment("50")
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
                FactPartyRole.CLIENT, "50").build();

        consumed(fact);
        billing.publish(fact);
        await().atMost(CONSUME_WAIT).until(() -> billing.pending() == 0);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "50", "50");
        assertAll(() -> assertEquals(1, testMapper.countAllConsumedEvents()),
            () -> assertEquals(1, testMapper.selectPostings(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO, CURRENCY)
                .size()));
        AccountPostedProbe.assertContiguous(posted.await(1), STREAM);
    }

    /** 不同事实乱序到达（主键大的先到）：按到达顺序在同一流内分配 1、2、3，AccountPosted 版本连续。 */
    @Test
    void test_postingSequenceFollowsArrivalOrder() {
        BillingFactPayload first = clientCredit("1").build();
        BillingFactPayload second = clientCredit("2").build();
        BillingFactPayload third = clientCredit("3").build();

        consumed(third);
        consumed(first);
        consumed(second);

        List<Map<String, Object>> postings = testMapper.selectPostings(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO,
            CURRENCY);
        assertEquals(List.of(third.getFactId(), first.getFactId(), second.getFactId()),
            postings.stream().map(row -> ((Number)row.get("fact_id")).longValue()).toList());
        assertEquals(3L, ((Number)testMapper.selectStream(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO, CURRENCY)
            .get("last_sequence")).longValue());
        List<AccountPostedProbe.Posted> events = posted.await(3);
        AccountPostedProbe.assertContiguous(events, STREAM);
        assertEquals(third.getFactId(), events.getFirst().payload().getFactId());
    }

    /** 替代后到：版本 2 的批次先冲回版本 1 的流水（reverses_entry_id 指向原流水）再写新行；版本 1 标记 REVERSED。 */
    @Test
    void test_version2SupersedesVersion1() {
        BillingFactPayload v1 = clientCredit("10").build();
        BillingFactPayload v2 = clientCredit("12").supersedes(v1.getFactId(), 2).build();

        consumed(v1);
        consumed(v2);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "10", "22", "12");
        long original = ((Number)factEntries(v1.getFactId()).getFirst().get("id")).longValue();
        List<Map<String, Object>> entries = factEntries(v2.getFactId());
        assertAll(() -> assertEquals(AppliedFactStatus.REVERSED.getCode(), applyStatus(v1.getFactId())),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(v2.getFactId())),
            () -> assertEquals(2, entries.size()),
            () -> assertEquals(EntrySide.DEBIT.getCode(), entries.get(0).get("entry_side")),
            () -> assertEquals(original, ((Number)entries.get(0).get("reverses_entry_id")).longValue()),
            () -> assertNull(entries.get(1).get("reverses_entry_id")));

        AccountPostedPayload payload = posted.await(2).get(1).payload();
        assertAll(() -> assertEquals(v1.getFactId(), payload.getSupersedesFactId()),
            () -> assertEquals(original, payload.getEntries().get(0).getReversesEntryId()),
            () -> assertEquals(FactApplyStatus.APPLIED.getCode(), payload.getFactApplyStatus()));
    }

    /** 替代先到：版本 2 先入账并预登记版本 1；版本 1 到达后只生成 0 流水的 SKIPPED 批次，余额只反映版本 2。 */
    @Test
    void test_version2BeforeVersion1() {
        BillingFactPayload v1 = clientCredit("10").build();
        BillingFactPayload v2 = clientCredit("12").supersedes(v1.getFactId(), 2).build();

        consumed(v2);
        assertEquals(AppliedFactStatus.PENDING_SUPERSEDED.getCode(), applyStatus(v1.getFactId()));
        consumed(v1);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "12", "12");
        assertAll(() -> assertEquals(AppliedFactStatus.SKIPPED.getCode(), applyStatus(v1.getFactId())),
            () -> assertEquals(0, ((Number)factPosting(v1.getFactId()).get("entry_count")).intValue()));
        List<AccountPostedProbe.Posted> events = posted.await(2);
        AccountPostedPayload skipped = events.get(1).payload();
        assertAll(() -> assertEquals(v1.getFactId(), skipped.getFactId()),
            () -> assertEquals(FactApplyStatus.SKIPPED.getCode(), skipped.getFactApplyStatus()),
            () -> assertTrue(skipped.getEntries().isEmpty()));
    }

    /** 人工调整按付方 / 收方映射：Client 收方记贷方、付方记借方，Partner 付方记借方；经营主体角色不进明细账。 */
    @Test
    void test_adjustmentRolesMapToAccounts() {
        BillingFactPayload fact = adjustment().partner(PARTNER_ID, FINANCE_ENTITY_ID)
            .line(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT,
                FactLineDirection.CHARGE, "10")
            .line(FactComponentType.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactLineDirection.ADJUSTMENT, "1")
            .line(FactComponentType.PARTNER_SHARE, FactPartyRole.LOCAL_PARTNER, PARTNER, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, FactLineDirection.CHARGE, "4")
            .build();

        consumed(fact);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "1", "10", "9");
        assertBalance(OwnerAccountType.PARTNER_PAYABLE, PARTNER, BalanceBucket.PENDING, "4", "0", "-4");
        assertEquals(2, testMapper.selectAccounts().size(), "经营主体角色不开立账户");
        posted.await(1);
    }

    /** 成本调整：渠道与经营主体之间的 CHANNEL_COST 不影响 Owner，仍生成 0 流水批次并发布。 */
    @Test
    void test_costAdjustmentPostsEmptyBatch() {
        BillingFactPayload fact = BillingFactBuilder.costAdjustment(nextFactId(), CLIENT_ID, ENTITY_NO, CURRENCY,
            CHANNEL_ACCOUNT_ID).line(FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.CHANNEL, BillingFactBuilder.CHANNEL_ID, FactLineDirection.ADJUSTMENT, "3").build();

        consumed(fact);

        assertAll(() -> assertTrue(testMapper.selectAccounts().isEmpty()),
            () -> assertEquals(0, ((Number)factPosting(fact.getFactId()).get("entry_count")).intValue()),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(fact.getFactId())));
        AccountPostedPayload payload = posted.await(1).getFirst().payload();
        assertAll(() -> assertEquals(FactApplyStatus.APPLIED.getCode(), payload.getFactApplyStatus()),
            () -> assertTrue(payload.getEntries().isEmpty()));
    }

    /** CLOSED 账户不拒绝事实：照常入账（只记 WARN），状态不变。 */
    @Test
    void test_closedAccountStillPosts() {
        long accountId = accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT,
            BalanceBucket.PENDING, "0");
        accounts.status(accountId, AccountStatus.CLOSED);

        consumed(clientCredit("5").build());

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "5", "5");
        assertEquals(AccountStatus.CLOSED.getCode(), account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING)
            .get("account_status"));
        posted.await(1);
    }

    /** 事实带到期时间且有 PENDING 贷方流水：处理记录落库到期时间并标记 PENDING；无到期时间为 NOT_APPLICABLE。 */
    @Test
    void test_balanceAvailableTimeRecorded() {
        Instant availableTime = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        BillingFactPayload dated = payment("20")
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CLIENT, "20")
            .balanceAvailableTime(availableTime).build();
        BillingFactPayload undated = clientCredit("1").build();

        consumed(dated);
        consumed(undated);

        Map<String, Object> datedRow = testMapper.selectAppliedFact(dated.getFactId());
        assertAll(() -> assertEquals(MaturityStatus.PENDING.getCode(), datedRow.get("maturity_status")),
            () -> assertEquals(availableTime,
                ((LocalDateTime)datedRow.get("balance_available_time")).toInstant(ZoneOffset.UTC)),
            () -> assertEquals(MaturityStatus.NOT_APPLICABLE.getCode(),
                testMapper.selectAppliedFact(undated.getFactId()).get("maturity_status")));
        posted.await(2);
    }

    /** REVERSAL 方向：收方的贷方影响改为借方。 */
    @Test
    void test_reversalDirectionSwapsSides() {
        BillingFactPayload fact = adjustment().line(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.REVERSAL, "5").build();

        consumed(fact);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "5", "0", "-5");
        posted.await(1);
    }

    /** ledger 没有记账规则的角色（CONSUMER）：两边一致拒绝，进入死信，不生成批次。 */
    @Test
    void test_consumerRoleRejectedToDeadLetter() {
        BillingFactPayload fact = adjustment().line(FactComponentType.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT,
            FactPartyRole.CONSUMER, "1", FactLineDirection.CHARGE, "1").build();

        billing.publish(fact);

        await().atMost(CONSUME_WAIT).until(() -> billing.deadLetters() == 1);
        assertRejected(fact);
    }

    /** Owner 角色出现在没有映射的费用类型上：整条事实拒绝进入死信，不登记消费、不开立账户、不生成批次。 */
    @Test
    void test_unmappedOwnerFeeTypeRejectedToDeadLetter() {
        BillingFactPayload fact = adjustment().line(FactComponentType.SELLER_TAX, FactPartyRole.CLIENT, CLIENT,
            FactPartyRole.TAX_AUTHORITY, "4001", FactLineDirection.CHARGE, "1").build();

        billing.publish(fact);

        await().atMost(CONSUME_WAIT).until(() -> billing.deadLetters() == 1);
        assertRejected(fact);
    }

    /** 拒绝的事实不留下任何本地事实。 */
    private void assertRejected(BillingFactPayload fact) {
        assertAll(() -> assertEquals(0, testMapper.countAllConsumedEvents()),
            () -> assertTrue(testMapper.selectAccounts().isEmpty()), () -> assertNull(applyStatus(fact.getFactId())),
            () -> assertNull(testMapper.selectFactPosting(fact.getFactId())),
            () -> assertEquals(0, testMapper.countCompensationEvents()));
    }

    /** 断言一条载荷流水的账户身份、方向与事实行号。 */
    private static void assertEntry(AccountPostedEntryPayload entry, OwnerAccountType type, String ownerId, EntrySide side,
        int factLineNo) {
        assertAll(() -> assertEquals(type.getCode(), entry.getAccountCode()),
            () -> assertEquals(type.getOwnerType().getCode(), entry.getOwnerType()),
            () -> assertEquals(ownerId, entry.getOwnerId()),
            () -> assertEquals(BalanceBucket.PENDING.getCode(), entry.getBalanceBucket()),
            () -> assertEquals(side.getCode(), entry.getEntrySide()),
            () -> assertEquals(factLineNo, entry.getFactLineNo()),
            () -> assertNull(entry.getReversesEntryId()));
    }
}
