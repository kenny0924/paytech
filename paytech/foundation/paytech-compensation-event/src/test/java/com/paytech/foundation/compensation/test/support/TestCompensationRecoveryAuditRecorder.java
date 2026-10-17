package com.paytech.foundation.compensation.test.support;

import java.util.Objects;

import com.paytech.foundation.compensation.api.CompensationRecoveryAudit;
import com.paytech.foundation.compensation.api.CompensationRecoveryAuditRecorder;
import com.paytech.foundation.compensation.test.mapper.TestMapper;

/**
 * 在与重开操作相同的本地事务中持久化恢复审计记录。
 *
 * @author Codex
 */
public final class TestCompensationRecoveryAuditRecorder implements CompensationRecoveryAuditRecorder {

    private final TestMapper testMapper;

    public TestCompensationRecoveryAuditRecorder(TestMapper testMapper) {
        this.testMapper = Objects.requireNonNull(testMapper, "testMapper must not be null");
    }

    @Override
    public void record(CompensationRecoveryAudit audit) {
        testMapper.insertRecoveryAudit(audit);
    }
}
