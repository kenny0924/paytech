package com.paytech.finance.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.finance.FinanceApplication;
import com.paytech.finance.test.checker.FinanceResponseChecker;
import com.paytech.finance.test.checker.LedgerChecker;
import com.paytech.finance.test.clear.FinanceDataCleaner;
import com.paytech.finance.test.clear.FinanceRedisCleaner;
import com.paytech.finance.test.mocker.local.LedgerMocker;
import com.paytech.finance.test.mocker.local.TransactionEconomicsMocker;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * Finance 服务级入口测试基类
 * <li>复用正式启动类 FinanceApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL/Redis 需预先就绪。</li>
 * <li>Finance 本期没有外部 HTTP 依赖和异步任务，因此不启动 Mock HTTP 服务，也无需等待后台工作。</li>
 * <li>全局清理由 Foundation TestBase 在 AfterEach 执行全部 Clear 组件。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = FinanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({FinanceDataCleaner.class, FinanceRedisCleaner.class, LedgerMocker.class, LedgerChecker.class,
    TransactionEconomicsMocker.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Finance.Test_01";

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected LedgerMocker ledgerMocker;

    @Autowired
    protected LedgerChecker ledgerChecker;

    @Autowired
    private FinanceDataCleaner dataCleaner;

    /** 全局初始化：只检查空基线，不承接上一例状态，也不删除任何数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        dataCleaner.assertEmpty();
    }

    /** 发送内部 POST 请求。 */
    protected FinanceResponseChecker post(String path, Object body) {
        return new FinanceResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected FinanceResponseChecker get(String path) {
        return new FinanceResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }
}
