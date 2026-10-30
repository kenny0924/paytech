package com.paytech.commercial.test.mock.external.clientmaster;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.client.master.api.classification.ClientBusinessClassificationApi;
import com.paytech.client.master.api.classification.response.ClientBusinessClassificationResponseBody;
import com.paytech.commercial.test.mocker.clientmaster.ClientClassificationMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/** client-master Client 经营分类接口的测试 HTTP 实现。 */
@RestController
@TestComponent
public class MockClientBusinessClassificationApi implements ClientBusinessClassificationApi {
    private final ClientClassificationMocker mocker;

    /** 与用例共享配置与记录。 */
    public MockClientBusinessClassificationApi(ClientClassificationMocker mocker) {
        this.mocker = mocker;
    }

    /** 返回本例配置的分类或断连；未配置的 Client 记为意外调用。 */
    @Override
    public ClientBusinessClassificationResponseBody getBusinessClassification(String clientId) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", ClientClassificationMocker.REQUEST_ID);
        Object configured = mocker.classification(clientId);
        if (Boolean.FALSE.equals(configured)) {
            throw MockHttpFault.disconnect();
        }
        if (configured == null) {
            throw new IllegalStateException("未配置的 Client 经营分类: " + clientId);
        }
        return (ClientBusinessClassificationResponseBody) configured;
    }
}
