package com.paytech.platform.support.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.platform.support.PlatformSupportTestApplication;
import com.paytech.platform.support.test.configuration.LocalMockEnvironmentInitializer;
import com.paytech.platform.support.test.mocker.local.ApiCredentialMocker;
import com.paytech.platform.support.test.util.ApiCredentialQueryObserver;

import tools.jackson.databind.ObjectMapper;

/**
 * 服务级 Mock 入口测试基类：复用正式启动类，请求经过真实 Controller → Processor → Service →
 * Repository → Mapper → 本地 MySQL/Redis；外部依赖由各切片自己的 Mock Controller 返回配置 DTO。
 *
 * <p>全局检查隔离数据的空基线，各具体测试类准备本类事实；当前 PLT-I03 没有全局业务配置或外部调用。
 * {@link TestBase} 注入所有 {@code Clear} 并在每例统一 {@code @AfterEach} 清理。
 */
@ActiveProfiles("mock")
@ContextConfiguration(initializers = LocalMockEnvironmentInitializer.class)
@AutoConfigureRestTestClient
@SpringBootTest(classes = PlatformSupportTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class MockTestBase extends TestBase {

    @Autowired
    protected ApiCredentialMocker credentialMocker;

    @Autowired
    protected ApiCredentialQueryObserver credentialQueryObserver;

    /** 全局初始化只检查上例清理和本地基础设施，不偷偷删除数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        credentialMocker.assertEmpty();
        credentialQueryObserver.assertEmpty();
    }

    @Autowired
    protected HttpTestInvoker httpTestInvoker;

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected ObjectMapper objectMapper;
}
