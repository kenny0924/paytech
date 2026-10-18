package com.paytech.channel.connector.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.channel.connector.ChannelConnectorApplication;
import com.paytech.channel.connector.test.configuration.ChannelConnectorTestConfiguration;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker;
import com.paytech.channel.connector.test.mocker.PlatformMocker;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.channel.connector.test.mocker.VaultTransactionObserver;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;

/** 共用正式 channel-connector 应用；每例验证专用库与 Mock 空基线，结束时检查意外调用后由 Foundation 统一清理。 */
@SpringBootTest(classes = ChannelConnectorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
@Import(ChannelConnectorTestConfiguration.class)
public abstract class ChannelConnectorMockTestBase extends TestBase {
    @Autowired
    protected ChannelDataMocker data;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected VaultTransactionObserver transactions;

    @Autowired
    protected PlatformMocker platform;

    @Autowired
    protected MockHttpServer mockHttpServer;

    /** 全局初始化只核对空基线，不在前置阶段删除数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        data.assertEmpty();
        vault.assertEmpty();
        platform.assertEmpty();
    }

    /** 所有 HTTP 请求结束并检查意外调用后，父类继续执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        mockHttpServer.awaitIdle();
        assertAll(vault::assertNoUnexpectedCalls, platform::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests, transactions::assertOutsideTransactions);
    }
}
