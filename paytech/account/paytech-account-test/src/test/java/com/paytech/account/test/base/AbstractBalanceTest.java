package com.paytech.account.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.test.builder.EconomicFactBuilder;

/**
 * 余额消费、一致性、重建与核对用例的公共常量与断言
 * <li>交易主体固定为 AE001/USD；ledger 账簿身份由 {@link com.paytech.account.test.mocker.external.LedgerBookMocker} 以 DTO 返回：
 * OE 账簿 {@value #OE_BOOK_ID}、平台 Finance Entity 账簿 {@value #FE_BOOK_ID}。</li>
 * <li>经济事实主键在本类内递增分配，与 billing 的 Snowflake 主键一样随登记顺序增大。</li>
 */
public abstract class AbstractBalanceTest extends MockTestBase {
    protected static final String ENTITY_NO = "AE001";

    protected static final String CURRENCY = "USD";

    protected static final long CLIENT_ID = 1001L;

    protected static final long PARTNER_ID = 2001L;

    protected static final long PARTNER_AGREEMENT_OE_ID = 2101L;

    protected static final long FINANCE_ENTITY_ID = 8001L;

    /** billing 在事实头中给出的渠道清算账户（reconciliation t_channel_account.id）。 */
    protected static final long CHANNEL_ACCOUNT_ID = 620001L;

    protected static final long OE_BOOK_ID = 7001L;

    protected static final long FE_BOOK_ID = 7002L;

    private static final AtomicLong FACT_IDS = new AtomicLong(910_000_000L);

    /** 两级初始化的类级部分：ledger 中存在 OE 与平台 Finance Entity 两个 USD 账簿。 */
    @BeforeEach
    void setUpBalanceMocks() {
        ledger.mockBook(OE_BOOK_ID, BookEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO, CURRENCY);
        ledger.mockBook(FE_BOOK_ID, BookEntityType.FINANCE_ENTITY.getCode(), Long.toString(FINANCE_ENTITY_ID), CURRENCY);
    }

    /** 下一个经济事实主键。 */
    protected static long nextFactId() {
        return FACT_IDS.incrementAndGet();
    }

    /** 无合作方的 PAYMENT 交易事实模板。 */
    protected static EconomicFactBuilder payment(String grossAmount) {
        return EconomicFactBuilder.payment(nextFactId(), CLIENT_ID, ENTITY_NO, CURRENCY, grossAmount, CHANNEL_ACCOUNT_ID);
    }

    /** MANUAL_ADJUSTMENT 人工调整事实模板。 */
    protected static EconomicFactBuilder adjustment() {
        return EconomicFactBuilder.adjustment(nextFactId(), CLIENT_ID, ENTITY_NO, CURRENCY);
    }

    /** 断言 AE001/USD 账簿内一个余额的借贷累计与净额。 */
    protected void assertBalance(String accountCode, OwnerType ownerType, String ownerId, BalanceBucket bucket,
        String debit, String credit, String net) {
        Map<String, Object> row = testMapper.selectBalance(ENTITY_NO, CURRENCY, accountCode, ownerType.getCode(), ownerId,
            bucket.getCode());
        assertNotNull(row, "余额应已建立: " + accountCode + "/" + ownerType.getCode() + "/" + ownerId);
        assertAll(accountCode + "/" + ownerType.getCode() + "/" + ownerId,
            () -> assertEquals(0, new BigDecimal(debit).compareTo((BigDecimal)row.get("debit_total")), "debit_total"),
            () -> assertEquals(0, new BigDecimal(credit).compareTo((BigDecimal)row.get("credit_total")), "credit_total"),
            () -> assertEquals(0, new BigDecimal(net).compareTo((BigDecimal)row.get("net_balance")), "net_balance"));
    }

    /** 已处理事实的状态码；不存在时为空。 */
    protected String applyStatus(long factId) {
        Map<String, Object> row = testMapper.selectAppliedFact(factId);
        return row == null ? null : (String)row.get("apply_status");
    }
}
