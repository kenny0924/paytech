package com.paytech.payout.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;
import com.paytech.payout.test.probe.TransactionBoundaryProbe;

/**
 * 只增加测试 Mapper、独立的 Vault / account / Platform HTTP Mock 与出站事务边界观察；生产装配由正式应用完成
 * <li>EnableMockHttpServer 扫描 test 包下全部 {@code @TestComponent}：Mock Controller 只进入独立 Mock HTTP 服务，其余 Mocker、Probe、
 * 清理组件注册为宿主 Bean。</li>
 * <li>Mock 服务的随机地址写入 test.mock-http.base-url，application-mock.yml 的 simple discovery 把 vault-service、account-service、
 * platform-support-service 指向它，真实出站代理与 LoadBalancer 保持原样。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.payout.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.payout.test.mock", "com.paytech.payout.test.mocker",
    "com.paytech.payout.test.probe", "com.paytech.payout.test.clear"}, baseUrlProperty = "test.mock-http.base-url")
public class PayoutTestConfiguration {

    /**
     * 在调用线程上观察 account 与 Platform 出站请求时是否处于数据库事务（AC-10）
     * <li>只追加只读观察拦截器，不改变请求；Vault 客户端清空宿主拦截器，其事务外约束由生产端口的事务检查保证。</li>
     */
    @Bean
    RestClientHttpServiceGroupConfigurer transactionBoundaryObserver(TransactionBoundaryProbe probe) {
        return groups -> groups.forEachGroup((group, client, proxy) -> client.requestInterceptor((request, body, execution) -> {
            probe.recordOutbound(group.name(), TransactionSynchronizationManager.isActualTransactionActive());
            return execution.execute(request, body);
        }));
    }
}
