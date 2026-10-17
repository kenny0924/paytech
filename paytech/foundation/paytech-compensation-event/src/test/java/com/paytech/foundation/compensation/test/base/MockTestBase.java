package com.paytech.foundation.compensation.test.base;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.TestApplication;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.test.mapper.TestMapper;
import com.paytech.foundation.compensation.test.support.TestHandlerProbe;

/**
 * 共用本地基础设施测试基类。
 *
 * @author Codex
 */
@ActiveProfiles("mock")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class MockTestBase {

    @Autowired
    protected TestMapper testMapper;

    @Autowired
    protected CompensationEventRegistrar registrar;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Autowired
    protected TestHandlerProbe handlerProbe;

    @BeforeEach
    protected void resetTestState() {
        testMapper.deleteAllRecoveryAudits();
        testMapper.deleteAllCompensationEvents();
        testMapper.deleteAllBusinessSources();
        handlerProbe.reset();
    }

    protected <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    protected void createBusinessSource(String eventKey) {
        testMapper.insertBusinessSource(eventKey, "TEST_SOURCE", "value-" + eventKey);
    }

    protected void createBusinessSource(String eventKey, String businessValue) {
        testMapper.insertBusinessSource(eventKey, "TEST_SOURCE", businessValue);
    }
}
