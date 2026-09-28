package com.paytech.client.contract.test.base.checkout;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.client.contract.ClientContractApplication;
import com.paytech.client.contract.domain.CheckoutQuery;
import com.paytech.client.contract.test.dal.checkout.CheckoutTestMapper;
import com.paytech.client.contract.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.client.contract.test.mocker.local.checkout.CheckoutResolutionFixture;
import com.paytech.foundation.testsupport.api.TestBase;

/** 正式应用和真实本地 MySQL；Risk 使用正式不可用端口，不注入批准替身。 */
@ActiveProfiles({ "auto", "checkout-auto" })
@SpringBootTest(classes = ClientContractApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({ CheckoutResolutionFixture.class, CheckoutTestMapper.class, AbstractCheckoutTest.TimeConfiguration.class })
public abstract class AbstractCheckoutTest extends TestBase {
    @Autowired
    protected CheckoutResolutionFixture fixture;

    /** 两级初始化的公共基线检查，具体业务数据由子类 BeforeEach 准备。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        fixture.assertEmpty();
    }

    /** 为内部入口用例构造同一固定商业时点，不读取或改变数据库。 */
    protected CheckoutQuery query() {
        return CheckoutQuery.resolve(Long.toString(CheckoutFixture.CLIENT_ID), "ORDER-1", "AE", "USD", "en-AE",
                CheckoutFixture.NOW.toString(), CheckoutFixture.NOW);
    }

    /** 固定测试时点并约束测试 HTTP 客户端，不替换生产业务链路。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class TimeConfiguration {
        /** 每次断言只发出一次 HTTP 请求，禁止 503 自动重试和重定向。 */
        @Bean
        ClientHttpRequestFactory resolutionHttpRequestFactory() {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            var factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(Duration.ofSeconds(5));
            return factory;
        }

        /** 在每例无可变时钟状态的前提下使用同一固定业务时点。 */
        @Bean
        @Primary
        Clock resolutionTestClock() {
            return Clock.fixed(CheckoutFixture.NOW, ZoneOffset.UTC);
        }
    }
}
