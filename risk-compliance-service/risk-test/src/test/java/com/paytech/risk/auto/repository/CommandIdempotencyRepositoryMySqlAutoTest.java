package com.paytech.risk.auto.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.domain.enums.CommandOperationType;
import com.paytech.risk.domainservice.service.CommandIdempotencyService;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.CommandRequest;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.First;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.SucceededReplay;
import com.paytech.risk.test.base.RiskRepositoryAutoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用真实 MySQL 验证 no-op upsert 与锁读取的命令幂等合同。 */
class CommandIdempotencyRepositoryMySqlAutoTest extends RiskRepositoryAutoTestBase {

    private static final String ACTOR = "910000000000000011";

    @Autowired
    private CommandIdempotencyService commandIdempotencyService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void test_firstClaimSucceededReplayAndDifferentHashUseOneLockedMySqlRow() {
        String key = decisionTestMapper.uniqueNamespace();
        String callerScope = "OPERATIONS_ACCOUNT:" + ACTOR;
        String requestHash = "a".repeat(64);
        CommandRequest request = request(key, requestHash);
        Instant firstNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.executeWithoutResult(ignored -> {
                assertInstanceOf(First.class, commandIdempotencyService.claim(request, firstNow));
                commandIdempotencyService.complete(request, CommandIdempotencyService.responseReference(
                    CommandOperationType.CREATE_TEST_DECISION_RELEASE, "900001"), firstNow);
            });

            SucceededReplay replay = transaction.execute(ignored -> assertInstanceOf(SucceededReplay.class,
                commandIdempotencyService.claim(request, firstNow.plusSeconds(1))));
            assertEquals("release:900001", replay.responseReference().value());

            CommandRequest differentHash = request(key, "b".repeat(64));
            assertThrows(ConcurrentRequestException.class, () -> transaction.execute(ignored ->
                commandIdempotencyService.claim(differentHash, firstNow.plusSeconds(2))));

            assertEquals(1, decisionTestMapper.ownedCommandCount(
                callerScope, CommandOperationType.CREATE_TEST_DECISION_RELEASE.name(), key));
            Map<String, Object> persisted = jdbc.queryForMap(
                "SELECT request_hash,response_ref,status FROM t_command_idempotency "
                    + "WHERE caller_scope=? AND operation_type=? AND idempotency_key=?",
                callerScope, CommandOperationType.CREATE_TEST_DECISION_RELEASE.name(), key);
            assertEquals(requestHash, persisted.get("request_hash"));
            assertEquals("release:900001", persisted.get("response_ref"));
            assertEquals("SUCCEEDED", persisted.get("status"));
        } finally {
            decisionTestMapper.deleteOwnedCommand(
                callerScope, CommandOperationType.CREATE_TEST_DECISION_RELEASE.name(), key);
        }
    }

    private static CommandRequest request(String key, String requestHash) {
        return CommandIdempotencyService.request(
            CommandIdempotencyService.identity(
                ACTOR, CommandOperationType.CREATE_TEST_DECISION_RELEASE, key),
            "RISK_CREATE_TEST_RELEASE_COMMAND_V1",
            requestHash);
    }
}
