package com.paytech.payout.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.payout.PayoutApplication;
import com.paytech.payout.api.PayoutHeaders;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.clear.PayoutDataCleaner;
import com.paytech.payout.test.configuration.PayoutTestConfiguration;
import com.paytech.payout.test.dal.PayoutTestMapper;
import com.paytech.payout.test.mocker.external.AccountBalanceMocker;
import com.paytech.payout.test.mocker.external.PlatformNotificationMocker;
import com.paytech.payout.test.mocker.external.VaultMocker;
import com.paytech.payout.test.mocker.local.BeneficiaryAccountMocker;
import com.paytech.payout.test.probe.TransactionBoundaryProbe;
import com.paytech.payout.test.support.PayoutTestConstants;

import tools.jackson.databind.ObjectMapper;

/**
 * payout 服务级入口测试基类
 * <li>复用正式启动类 PayoutApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_payout）与 Redis（前缀 payout-mock）
 * 需预先就绪，缺表时 BeforeEach 明确失败并提示按测试 init/ddl.sql 手工建表。</li>
 * <li>出站 HTTP：Vault、account 余额列表与 Platform 通知经真实客户端打到独立 Mock HTTP 服务，由对应 Mocker 按用例配置 DTO；未配置的调用在
 * AfterEach 使测试失败。account 与 Platform 出站是否处于事务由 {@link TransactionBoundaryProbe} 在调用线程上记录。</li>
 * <li>每例开始检查库与 Mock 空基线；结束时等待 Mock 请求与在途补偿任务（INIT / PROCESSING）结束、检查意外调用与出站事务边界，再由 Foundation
 * TestBase 执行全部 Clear。异步工作未在上限内结束时环境标记为不可复用。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = PayoutApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PayoutTestConfiguration.class)
public abstract class MockTestBase extends TestBase {
    /** 等待补偿任务完成的上限。 */
    protected static final Duration ASYNC_WAIT = Duration.ofSeconds(20);

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PayoutTestMapper testMapper;

    @Autowired
    protected BeneficiaryAccountMocker accounts;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected AccountBalanceMocker account;

    @Autowired
    protected PlatformNotificationMocker platform;

    @Autowired
    protected TransactionBoundaryProbe transactions;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    private PayoutDataCleaner dataCleaner;

    /** 全局初始化：检查库与 Mock 空基线，不删除任何数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        dataCleaner.assertEmpty();
        vault.assertEmpty();
        account.assertEmpty();
        platform.assertEmpty();
    }

    /** 等待 Mock HTTP 请求与在途补偿任务结束并检查意外调用；异步未结束时标记环境不可复用。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            mockHttpServer.awaitIdle();
            await().atMost(ASYNC_WAIT).until(() -> testMapper.countInFlightCompensationEvents() == 0);
        } catch (RuntimeException | AssertionError failure) {
            dataCleaner.markUnusable();
            throw new AssertionError("异步工作未结束: " + testMapper.selectCompensationEvents(), failure);
        }
        assertAll(vault::assertNoUnexpectedCalls, account::assertNoUnexpectedCalls, platform::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests, transactions::assertNoOutboundInTransaction);
    }

    /** 后台仍可能写入时拒绝清理。 */
    @Override
    protected void checkClearAllowed() {
        dataCleaner.assertReusable();
    }

    /** 内部请求头，指定调用方服务名。 */
    protected static HttpHeaders caller(String callerService) {
        HttpHeaders headers = TestRequestHeaders.internal(PayoutTestConstants.REQUEST_ID);
        headers.set(PayoutHeaders.CALLER_SERVICE, callerService);
        return headers;
    }

    /** 以 Client BFF 身份 POST。 */
    protected PayoutResponseChecker post(String path, Object body) {
        return post(path, caller(PayoutTestConstants.CLIENT_BFF), body);
    }

    /** 以指定请求头 POST。 */
    protected PayoutResponseChecker post(String path, HttpHeaders headers, Object body) {
        return new PayoutResponseChecker(http.post(path, headers, body), objectMapper, PayoutTestConstants.REQUEST_ID);
    }

    /** 以 Client BFF 身份 GET。 */
    protected PayoutResponseChecker get(String path) {
        return get(path, caller(PayoutTestConstants.CLIENT_BFF));
    }

    /** 以指定请求头 GET。 */
    protected PayoutResponseChecker get(String path, HttpHeaders headers) {
        return new PayoutResponseChecker(http.get(path, headers), objectMapper, PayoutTestConstants.REQUEST_ID);
    }
}
