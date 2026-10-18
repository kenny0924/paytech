package com.paytech.data.test.base;

import com.paytech.data.DataServiceApplication;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 服务级 Mock 入口测试基类：复用正式启动类 {@link DataServiceApplication}，{@code mock} Profile 关闭 Doris。
 *
 * <p>
 * 阶段 0 的数据服务没有数据库、外部 HTTP 依赖或任何可变测试状态，因此不继承 {@code TestBase}：它要求至少一个
 * {@code Clear} Bean，而这里没有需要清理的对象。首个 Mocker 出现时改为继承 {@code TestBase}。
 *
 * @author Claude
 */
@ActiveProfiles("mock")
@SpringBootTest(classes = DataServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class MockTestBase {

    @LocalManagementPort
    private int managementPort;

    /**
     * 返回指向管理端口（Actuator）的 HTTP 客户端。
     *
     * @return 管理端口客户端
     */
    protected RestTestClient managementClient() {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build();
    }
}
