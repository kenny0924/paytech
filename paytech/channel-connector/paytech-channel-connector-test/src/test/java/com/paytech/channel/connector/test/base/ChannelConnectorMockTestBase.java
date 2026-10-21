package com.paytech.channel.connector.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.channel.connector.ChannelConnectorApplication;
import com.paytech.channel.connector.test.configuration.ChannelConnectorTestConfiguration;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker;
import com.paytech.channel.connector.test.mocker.ChannelMocker;
import com.paytech.channel.connector.test.mocker.PaymentCardMocker;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.channel.connector.test.mocker.VaultTransactionObserver;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;

/**
 * 共用正式 channel-connector 应用；每例验证专用库与 Mock 空基线，结束时检查意外调用后由 Foundation 统一清理
 * <li>本地 MySQL（mor_channel_connector）与 Redis（资源锁，前缀 channel-connector-mock）需预先就绪。</li>
 * <li>{@code @AutoConfigureRestTestClient} 提供公共 HttpTestInvoker，供各管理接口测试经真实 HTTP 入口调用。</li>
 * <li>外部依赖：Vault（账号配置读取与写入）、card-service（渠道协议读卡）与合成外部渠道，均由同一个独立 Mock HTTP 服务承载。</li>
 */
@SpringBootTest(classes = ChannelConnectorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@Import(ChannelConnectorTestConfiguration.class)
public abstract class ChannelConnectorMockTestBase extends TestBase {
    @Autowired
    protected ChannelDataMocker data;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected VaultTransactionObserver transactions;

    @Autowired
    protected ChannelMocker channel;

    @Autowired
    protected PaymentCardMocker paymentCard;

    @Autowired
    protected MockHttpServer mockHttpServer;

    /** 全局初始化只核对空基线，不在前置阶段删除数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        data.assertEmpty();
        vault.assertEmpty();
        channel.assertEmpty();
        paymentCard.assertEmpty();
    }

    /** 所有 HTTP 请求结束并检查意外调用后，父类继续执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        mockHttpServer.awaitIdle();
        assertAll(vault::assertNoUnexpectedCalls, channel::assertNoUnexpectedCalls, paymentCard::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests, transactions::assertOutsideTransactions);
    }
}
