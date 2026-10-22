package com.paytech.channel.connector.auto.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.api.enums.VerifyStepStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRunStep;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunStepRepository;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;

/**
 * 沙箱验证数据层冒烟：真实 Mapper/XML、CodeEnum TypeHandler、唯一索引、版本 CAS、运行结论只写一次与每用例最近运行。
 * 管理接口与运行的业务矩阵在第 3 步由 HTTP 入口覆盖；四表由 ChannelDataMocker 整表回收。
 *
 * @see ChannelVerifyAccountRepository
 * @see ChannelVerifyCaseRepository
 * @see ChannelVerifyRunRepository
 * @see ChannelVerifyRunStepRepository
 */
class VerifyRepositorySmokeTest extends ChannelConnectorMockTestBase {

    private static final long OPERATOR_ID = 900_000_000_000_000_001L;

    @Autowired
    private ChannelVerifyAccountRepository accountRepository;

    @Autowired
    private ChannelVerifyCaseRepository caseRepository;

    @Autowired
    private ChannelVerifyRunRepository runRepository;

    @Autowired
    private ChannelVerifyRunStepRepository stepRepository;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private final long channelId = randomId();

    /** 账号 JSON 原样往返；更新递增版本，旧版本更新与删除被拒绝；编码唯一索引转为业务异常。 */
    @Test
    void test_accountVersionCasAndDuplicate() {
        ChannelVerifyAccount row = account("UAT_MAIN");
        ChannelVerifyAccount stale = accountRepository.byCode(channelId, "UAT_MAIN");
        assertEquals("{\"appId\": \"app\"}", stale.getParameters());

        accountRepository.update(row, "主账号", "PAYERMAX", "A2", "E2", "{}", "{\"k\": \"v\"}", OPERATOR_ID, now.plusSeconds(1));
        ChannelVerifyAccount current = accountRepository.lock(channelId, "UAT_MAIN");
        assertAll(() -> assertEquals(1, current.getVersion()), () -> assertEquals("A2", current.getChannelAccountId()),
            () -> assertEquals("{\"k\": \"v\"}", current.getCredentials()),
            () -> assertEquals(current.getVerifyAccountId(), accountRepository.byId(row.getVerifyAccountId()).getVerifyAccountId()));
        assertThrows(ConcurrentRequestException.class,
            () -> accountRepository.update(stale, "旧", "C", "A", "E", "{}", "{}", OPERATOR_ID, now));
        assertThrows(ConcurrentRequestException.class, () -> accountRepository.delete(stale));
        assertThrows(BizCheckFailException.class, () -> account("UAT_MAIN"));
        assertEquals(List.of("UAT_MAIN"), accountRepository.allByChannel(channelId).stream()
            .map(ChannelVerifyAccount::getAccountCode).toList());
        accountRepository.delete(current);
        assertNull(accountRepository.byCode(channelId, "UAT_MAIN"));
    }

    /** 用例按编码筛选分页、按账号计数，更新与删除按版本 CAS。 */
    @Test
    void test_casePagingAndAccountReference() {
        ChannelVerifyAccount account = account("UAT");
        ChannelVerifyCase first = verifyCase("PAY_THEN_QUERY", account);
        verifyCase("QUERY_MISSING", account);
        assertThrows(BizCheckFailException.class, () -> verifyCase("PAY_THEN_QUERY", account));

        assertEquals(2, caseRepository.countByAccount(account.getVerifyAccountId()));
        assertEquals(1, caseRepository.count(channelId, "PAY%"));
        assertEquals(List.of("PAY_THEN_QUERY", "QUERY_MISSING"), caseRepository.list(channelId, null, 0, 10).stream()
            .map(ChannelVerifyCase::getCaseCode).toList());

        caseRepository.update(first, "下单后查询", null, account.getVerifyAccountId(), "[{\"id\": \"pay\"}]", OPERATOR_ID, now);
        assertEquals(1, caseRepository.byCode(channelId, "PAY_THEN_QUERY").getVersion());
        caseRepository.delete(caseRepository.lock(channelId, "PAY_THEN_QUERY"));
        assertEquals(1, caseRepository.countByAccount(account.getVerifyAccountId()));
    }

    /** 运行结论只在 RUNNING 时写入一次；最近运行与最近已结束运行按用例区分；步骤按序号读回。 */
    @Test
    void test_runFinishOnceAndLatest() {
        ChannelVerifyCase verifyCase = verifyCase("PAY", account("UAT"));
        ChannelVerifyRun passed = run(verifyCase, now);
        runRepository.finish(passed, VerifyRunStatus.PASSED, "{\"integration\": 1}", "[]", null, now.plusSeconds(2));
        assertThrows(ConcurrentRequestException.class,
            () -> runRepository.finish(passed, VerifyRunStatus.FAILED, null, null, "x", now.plusSeconds(3)));
        ChannelVerifyRun running = run(verifyCase, now.plusSeconds(10));

        long caseId = verifyCase.getVerifyCaseId();
        assertAll(
            () -> assertEquals(running.getVerifyRunId(), runRepository.latestByCases(List.of(caseId)).get(caseId).getVerifyRunId()),
            () -> assertEquals(passed.getVerifyRunId(), runRepository.latestFinishedByCases(List.of(caseId)).get(caseId).getVerifyRunId()),
            () -> assertEquals(VerifyRunStatus.PASSED, runRepository.byId(passed.getVerifyRunId()).getRunStatus()),
            () -> assertEquals(2, runRepository.count(channelId, caseId)),
            () -> assertEquals(running.getVerifyRunId(), runRepository.list(channelId, null, 0, 1).getFirst().getVerifyRunId()),
            () -> assertTrue(runRepository.latestByCases(List.of()).isEmpty()));

        stepRepository.insertAll(List.of(step(passed, 2, VerifyStepStatus.SKIPPED), step(passed, 1, VerifyStepStatus.PASSED)));
        stepRepository.insertAll(List.of());
        List<ChannelVerifyRunStep> steps = stepRepository.byRun(passed.getVerifyRunId());
        assertEquals(List.of(1, 2), steps.stream().map(ChannelVerifyRunStep::getStepNo).toList());
        assertEquals(VerifyStepStatus.SKIPPED, steps.get(1).getStepStatus());
    }

    /** 插入一个验证账号，版本 0。 */
    private ChannelVerifyAccount account(String code) {
        ChannelVerifyAccount row = new ChannelVerifyAccount(randomId(), channelId, code, "账号", "PAYERMAX", "A1", "E1",
            "{\"appId\": \"app\"}", "{\"merchantPrivateKey\": \"k\"}", 0, OPERATOR_ID, OPERATOR_ID, now, now);
        accountRepository.insert(row);
        return row;
    }

    /** 插入一个引用账号的验证用例，版本 0。 */
    private ChannelVerifyCase verifyCase(String code, ChannelVerifyAccount account) {
        ChannelVerifyCase row = new ChannelVerifyCase(randomId(), channelId, code, "用例", null,
            account.getVerifyAccountId(), "[]", 0, OPERATOR_ID, OPERATOR_ID, now, now);
        caseRepository.insert(row);
        return row;
    }

    /** 插入一次 RUNNING 运行。 */
    private ChannelVerifyRun run(ChannelVerifyCase verifyCase, Instant start) {
        ChannelVerifyRun row = new ChannelVerifyRun(randomId(), verifyCase.getVerifyCaseId(), channelId,
            VerifyRunStatus.RUNNING, "{}", null, null, null, OPERATOR_ID, start, null, start, start);
        runRepository.insert(row);
        return row;
    }

    /** 构造一条运行步骤。 */
    private ChannelVerifyRunStep step(ChannelVerifyRun run, int stepNo, VerifyStepStatus status) {
        return new ChannelVerifyRunStep(randomId(), run.getVerifyRunId(), stepNo, "s" + stepNo, "PAY", status,
            null, null, null, null, null, now);
    }

    /** 随机 13 位测试主键。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_000_000_000_000L);
    }
}
