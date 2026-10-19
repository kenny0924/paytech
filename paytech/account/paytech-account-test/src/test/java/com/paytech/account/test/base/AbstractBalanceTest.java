package com.paytech.account.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.test.builder.BillingFactBuilder;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;

/**
 * 明细账入账、桶移动、到期与查询用例的公共常量与断言
 * <li>交易主体固定为 AE001/USD，入账流聚合 ID {@code OE.AE001.USD.0}。</li>
 * <li>计费事实主键在本类内递增分配，与 billing 的 Snowflake 主键一样随登记顺序增大。</li>
 */
public abstract class AbstractBalanceTest extends MockTestBase {
    protected static final String ENTITY_NO = "AE001";

    protected static final String CURRENCY = "USD";

    protected static final long CLIENT_ID = 1001L;

    protected static final String CLIENT = String.valueOf(CLIENT_ID);

    protected static final long PARTNER_ID = 2001L;

    protected static final String PARTNER = String.valueOf(PARTNER_ID);

    protected static final long FINANCE_ENTITY_ID = 8001L;

    /** billing 以 CLEARING_ACCOUNT 外部引用给出的渠道清算账户（只在 ledger 记账）。 */
    protected static final long CHANNEL_ACCOUNT_ID = 620001L;

    /** AE001/USD 分片 0 的入账流聚合 ID。 */
    protected static final String STREAM = AccountPostedPayload.aggregateId(AccountEntityType.OPERATING_ENTITY, ENTITY_NO,
        CURRENCY, 0);

    private static final AtomicLong FACT_IDS = new AtomicLong(910_000_000L);

    /** 下一个计费事实主键。 */
    protected static long nextFactId() {
        return FACT_IDS.incrementAndGet();
    }

    /** 无合作方的 PAYMENT 交易事实模板。 */
    protected static BillingFactBuilder payment(String grossAmount) {
        return BillingFactBuilder.payment(nextFactId(), CLIENT_ID, ENTITY_NO, CURRENCY, grossAmount, CHANNEL_ACCOUNT_ID);
    }

    /** MANUAL_ADJUSTMENT 人工调整事实模板。 */
    protected static BillingFactBuilder adjustment() {
        return BillingFactBuilder.adjustment(nextFactId(), CLIENT_ID, ENTITY_NO, CURRENCY);
    }

    /** 给 Client 记一笔 PENDING 贷方的人工调整。 */
    protected static BillingFactBuilder clientCredit(String amount) {
        return adjustment().line(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
            FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, amount);
    }

    /** 读取 AE001/USD 下一个 Owner 账户；不存在为空。 */
    protected Map<String, Object> account(OwnerAccountType type, String ownerId, BalanceBucket bucket) {
        return testMapper.selectAccount(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO, CURRENCY, type.getCode(),
            type.getOwnerType().getCode(), ownerId, bucket.getCode());
    }

    /** 断言 AE001/USD 下一个 Owner 账户的借贷累计与净额。 */
    protected void assertBalance(OwnerAccountType type, String ownerId, BalanceBucket bucket, String debit, String credit,
        String net) {
        Map<String, Object> row = account(type, ownerId, bucket);
        assertNotNull(row, "Owner 账户应已开立: " + type.getCode() + "/" + ownerId + "/" + bucket.getCode());
        assertAll(type.getCode() + "/" + ownerId + "/" + bucket.getCode(),
            () -> assertEquals(0, new BigDecimal(debit).compareTo((BigDecimal)row.get("debit_total")), "debit_total"),
            () -> assertEquals(0, new BigDecimal(credit).compareTo((BigDecimal)row.get("credit_total")), "credit_total"),
            () -> assertEquals(0, new BigDecimal(net).compareTo((BigDecimal)row.get("net_balance")), "net_balance"));
    }

    /** 已处理事实的状态码；不存在时为空。 */
    protected String applyStatus(long factId) {
        Map<String, Object> row = testMapper.selectAppliedFact(factId);
        return row == null ? null : (String)row.get("apply_status");
    }

    /** 一条事实生成的批次。 */
    protected Map<String, Object> factPosting(long factId) {
        Map<String, Object> posting = testMapper.selectFactPosting(factId);
        assertNotNull(posting, "事实应已生成批次: " + factId);
        return posting;
    }

    /** 一条事实批次的流水，按行号排序。 */
    protected List<Map<String, Object>> factEntries(long factId) {
        return testMapper.selectEntries(((Number)factPosting(factId).get("id")).longValue());
    }

    /** 金额比较（忽略标度）。 */
    protected static void assertAmount(String expected, Object actual, String field) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal)actual), field);
    }
}
