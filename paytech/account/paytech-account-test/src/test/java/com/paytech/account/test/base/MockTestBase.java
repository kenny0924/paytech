package com.paytech.account.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.account.AccountApplication;
import com.paytech.account.test.checker.AccountResponseChecker;
import com.paytech.account.test.clear.AccountDataCleaner;
import com.paytech.account.test.mocker.local.BalanceAccountMocker;
import com.paytech.account.test.mocker.local.LedgerFixtureJdbc;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * Account 服务级入口测试基类
 * <li>复用正式启动类 AccountApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_ledger，共库只读） 需预先就绪。</li>
 * <li>本服务本期没有外部 HTTP 依赖和异步任务，因此不启动 Mock HTTP 服务，也无需等待后台工作。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 * <li>全局清理由 Foundation TestBase 在 AfterEach 执行全部 Clear 组件。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = AccountApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AccountDataCleaner.class, LedgerFixtureJdbc.class, BalanceAccountMocker.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Account.Test_01";

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected BalanceAccountMocker balanceAccountMocker;

    @Autowired
    private AccountDataCleaner dataCleaner;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：只检查空基线，不承接上一例状态，也不删除任何数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        dataCleaner.assertEmpty();
    }

    /** 发送内部 POST 请求。 */
    protected AccountResponseChecker post(String path, Object body) {
        return new AccountResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected AccountResponseChecker get(String path) {
        return new AccountResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
