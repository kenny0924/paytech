package com.paytech.account.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.app.ext.job.BalanceMaturityJobHandler;
import com.paytech.account.core.enums.MaturityStatus;
import com.paytech.account.domain.model.MaturityRunResult;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.probe.AccountPostedProbe;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.fact.EconomicFactPayload;

/**
 * 应付余额到期转可用：显式触发生产 XXL-Job Handler，经 MatureBalancesProcessor 与 BalanceMaturityService 读写本地 MySQL
 * <li>到期事实对每个 PENDING 应付账户按净贷方金额做一次 FACT_MATURITY 移动并标记 MATURED；未到期不动；重复执行按 source_ref 幂等；
 * 被替代事实扣除冲回后净额为零不移动。</li>
 *
 * @see BalanceMaturityJobHandler#runOnce()
 */
class BalanceMaturityJobTest extends AbstractBalanceTest {
    @Autowired
    private BalanceMaturityJobHandler job;

    /** 到期：Client 与 Partner 的 PENDING 各移动到 AVAILABLE，事实标记 MATURED，批次序号接在事实批次之后。 */
    @Test
    void test_dueFactMatures() {
        EconomicFactPayload fact = payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID)
            .component(AmountSource.CLIENT_PRINCIPAL, "94").component(AmountSource.PARTNER_SHARE, "1")
            .balanceAvailableTime(past()).build();
        consumed(fact);

        MaturityRunResult result = job.runOnce();

        assertAll(() -> assertEquals(1, result.getMaturedFactCount()), () -> assertEquals(2, result.getMovementCount()),
            () -> assertEquals(0, result.getFailedFactCount()),
            () -> assertEquals(MaturityStatus.MATURED.getCode(), testMapper.selectAppliedFact(fact.getFactId())
                .get("maturity_status")));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "94", "94", "0");
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "0", "94", "94");
        assertBalance(OwnerAccountType.PARTNER_PAYABLE, PARTNER, BalanceBucket.AVAILABLE, "0", "1", "1");
        List<Map<String, Object>> movements = testMapper.selectMovements();
        long pendingAccount = ((Number)account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING).get("id"))
            .longValue();
        assertAll(() -> assertEquals(2, movements.size()),
            () -> assertEquals(MovementSourceType.FACT_MATURITY.getCode(), movements.getFirst().get("source_type")),
            () -> assertEquals(true, movements.stream().anyMatch(row -> (fact.getFactId() + ":1:" + pendingAccount)
                .equals(row.get("source_ref")))));
        AccountPostedProbe.assertContiguous(posted.await(3), STREAM);
    }

    /** 未到期：不移动，事实保持 PENDING。 */
    @Test
    void test_notDueUnchanged() {
        EconomicFactPayload fact = payment("10").component(AmountSource.CLIENT_PRINCIPAL, "10")
            .balanceAvailableTime(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        consumed(fact);

        MaturityRunResult result = job.runOnce();

        assertAll(() -> assertEquals(0, result.getMaturedFactCount()),
            () -> assertEquals(MaturityStatus.PENDING.getCode(), testMapper.selectAppliedFact(fact.getFactId())
                .get("maturity_status")),
            () -> assertEquals(List.of(), testMapper.selectMovements()));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "10", "10");
        posted.await(1);
    }

    /** 重复执行：第二轮没有到期事实，不重复移动。 */
    @Test
    void test_repeatedRunDoesNotRepeat() {
        consumed(payment("10").component(AmountSource.CLIENT_PRINCIPAL, "10").balanceAvailableTime(past()).build());
        job.runOnce();

        MaturityRunResult second = job.runOnce();

        assertAll(() -> assertEquals(0, second.getMaturedFactCount()), () -> assertEquals(0, second.getMovementCount()),
            () -> assertEquals(1, testMapper.selectMovements().size()));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "0", "10", "10");
        posted.await(2);
    }

    /** 被替代后到期：版本 1 扣除冲回后净额为零不移动，版本 2 按自身金额移动；两条事实都标记 MATURED。 */
    @Test
    void test_supersededFactMaturesNetOfReversal() {
        EconomicFactPayload v1 = clientCredit("10").balanceAvailableTime(past()).build();
        EconomicFactPayload v2 = clientCredit("12").supersedes(v1.getFactId(), 2).balanceAvailableTime(past()).build();
        consumed(v1);
        consumed(v2);

        MaturityRunResult result = job.runOnce();

        assertAll(() -> assertEquals(2, result.getMaturedFactCount()), () -> assertEquals(1, result.getMovementCount()),
            () -> assertAmount("12", testMapper.selectMovements().getFirst().get("amount"), "amount"));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "22", "22", "0");
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "0", "12", "12");
        posted.await(3);
    }

    /** 已到期的时间。 */
    private static Instant past() {
        return Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
    }
}
