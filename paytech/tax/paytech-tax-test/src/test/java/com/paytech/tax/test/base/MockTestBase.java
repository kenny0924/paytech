package com.paytech.tax.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.tax.TaxApplication;
import com.paytech.tax.test.checker.TaxResponseChecker;
import com.paytech.tax.test.clear.TaxDataCleaner;

import tools.jackson.databind.ObjectMapper;

/**
 * Tax 服务级入口测试基类
 * <li>复用正式启动类 TaxApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_tax） 需预先就绪。</li>
 * <li>本服务本期没有外部 HTTP 依赖和异步任务，因此不启动 Mock HTTP 服务，也无需等待后台工作。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 * <li>全局清理由 Foundation TestBase 在 AfterEach 执行全部 Clear 组件。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = TaxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TaxDataCleaner.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Tax.Test_01";

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private TaxDataCleaner dataCleaner;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：只检查空基线，不承接上一例状态，也不删除任何数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        dataCleaner.assertEmpty();
    }

    /** 发送内部 POST 请求。 */
    protected TaxResponseChecker post(String path, Object body) {
        return new TaxResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected TaxResponseChecker get(String path) {
        return new TaxResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
