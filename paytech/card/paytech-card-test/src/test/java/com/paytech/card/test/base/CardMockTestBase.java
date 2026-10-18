package com.paytech.card.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.card.CardApplication;
import com.paytech.card.test.configuration.CardTestConfiguration;
import com.paytech.card.test.dal.CardTestMapper;
import com.paytech.card.test.mocker.CardDataMocker;
import com.paytech.card.test.mocker.VaultMocker;
import com.paytech.card.test.mocker.VaultTransactionObserver;
import com.paytech.card.test.probe.PaymentEventPublisher;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;

/**
 * 共用正式 card 应用：每例验证 mor_card 空基线、清空本服务的 payment 分区队列，结束时检查意外调用并由 Foundation 统一清理。
 * <li>MockHttpServer 以服务发现名承载 Vault（卡 DEK）；PaymentSucceeded 由 {@link PaymentEventPublisher} 扮演 Payment 发到真实本机 RabbitMQ。</li>
 */
@SpringBootTest(classes = CardApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
@Import({CardTestConfiguration.class, PaymentEventPublisher.class})
public abstract class CardMockTestBase extends TestBase {
    @Autowired
    protected CardDataMocker data;

    @Autowired
    protected CardTestMapper mapper;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected VaultTransactionObserver transactions;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    protected PaymentEventPublisher payment;

    /** 全局初始化只核对空基线并清空本服务的分区队列，不在前置阶段删除业务数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        payment.purge();
        data.assertEmpty();
        vault.assertEmpty();
    }

    /** 所有 HTTP 请求结束并检查意外调用后，清空本服务分区队列，父类继续执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        mockHttpServer.awaitIdle();
        try {
            assertAll(vault::assertNoUnexpectedCalls, mockHttpServer::assertNoUnexpectedRequests,
                transactions::assertOutsideTransactions);
        } finally {
            payment.purge();
        }
    }
}
