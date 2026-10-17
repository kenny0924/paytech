package com.paytech.commerce.test.checkout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

import com.paytech.foundation.testsupport.api.Clear;

/** 观察真实调用方线程的事务状态，不以 Mock HTTP 线程的事务状态作判断。 */
@TestComponent
public final class CctTransactionProbe
    implements ClientHttpRequestInterceptor, RestClientHttpServiceGroupConfigurer, Clear {
    private final List<Boolean> transactions = new CopyOnWriteArrayList<>();

    /** 自动挂载到真实 CCT 客户端，保留原有服务发现与 HTTP 传输链。 */
    @Override
    public void configureGroups(Groups<RestClient.Builder> groups) {
        groups.filterByName("client-contract-service")
            .forEachClient((group, builder) -> builder.requestInterceptor(this));
    }

    /** 记录后始终执行原始 HTTP 传输，不调整请求或响应。 */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {
        transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
        return execution.execute(request, body);
    }

    /** 返回本场景实际发生的出站事务观察。 */
    public List<Boolean> transactionStates() {
        return List.copyOf(transactions);
    }

    /** 清理本测试上下文的观察，不改变 HTTP Mock 或业务状态。 */
    @Override
    public void clear() {
        transactions.clear();
    }
}
