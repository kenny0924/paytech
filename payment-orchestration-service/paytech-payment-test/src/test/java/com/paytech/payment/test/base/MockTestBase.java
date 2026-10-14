package com.paytech.payment.test.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.payment.PaymentApplication;
import com.paytech.payment.test.configuration.ChannelTestConfiguration;
import com.paytech.payment.test.mocker.ChannelDataMocker;
import com.paytech.payment.test.mocker.PlatformMocker;
import com.paytech.payment.test.mocker.RiskMocker;
import com.paytech.payment.test.mocker.VaultMocker;

import static org.junit.jupiter.api.Assertions.assertAll;

/** 共用正式 Payment 应用；每例验证专用库空基线，并由 Foundation 在最后统一清理。 */
@SpringBootTest(classes = PaymentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
@Import(ChannelTestConfiguration.class)
public abstract class MockTestBase extends TestBase {
    @Autowired
    protected ChannelDataMocker data;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected com.paytech.payment.test.mocker.VaultTransactionObserver transactions;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    protected PlatformMocker platform;

    @Autowired
    protected RiskMocker risk;

    /** 全局初始化只核对空基线，不在前置阶段删除数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        data.assertEmpty();
        vault.assertEmpty();
    }

    /** 所有 HTTP 请求结束并检查意外调用后，父类继续执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        mockHttpServer.awaitIdle();
        assertAll(vault::assertNoUnexpectedCalls, platform::assertNoUnexpectedCalls, risk::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests,
            transactions::assertOutsideTransactions);
    }
}
