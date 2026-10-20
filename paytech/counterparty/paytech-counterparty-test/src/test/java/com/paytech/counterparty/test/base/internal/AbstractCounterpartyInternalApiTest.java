package com.paytech.counterparty.test.base.internal;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.counterparty.test.base.CounterpartyMockTestBase;
import com.paytech.counterparty.test.dal.CustomerTestMapper;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/** acquiring 使用的 counterparty 内部接口共用的真实 HTTP 调用（私网路径 /counterparty/v1，无 Context Path）；不包含测试方法或新增生命周期。 */
public abstract class AbstractCounterpartyInternalApiTest extends CounterpartyMockTestBase {
    private static final String BASE = "/counterparty/v1";

    @Autowired
    protected CustomerTestMapper customerMapper;

    /** 当前运行隔离的主 Client。 */
    protected final long clientId() {
        return customerDataMocker.clientId();
    }

    /** 当前运行隔离的另一个 Client。 */
    protected final long otherClientId() {
        return customerDataMocker.otherClientId();
    }

    /** 建或取 Customer。 */
    protected final Response resolve(long clientId, String consumerReference) {
        var body = Map.of("clientId", Long.toString(clientId), "consumerReference", consumerReference);
        return response(httpTestInvoker.post(BASE + "/customers/resolve", TestRequestHeaders.internal(REQUEST_ID), body));
    }

    /** 按主键读取 Customer。 */
    protected final Response getCustomer(String customerId) {
        return response(httpTestInvoker.get(BASE + "/customers/" + customerId, TestRequestHeaders.internal(REQUEST_ID)));
    }

    /** 存卡资格。 */
    protected final Response eligibility(long clientId, long customerId) {
        var path = UriComponentsBuilder.fromPath(BASE + "/card-saving-eligibility").queryParam("clientId", clientId)
            .queryParam("customerId", customerId).toUriString();
        return response(httpTestInvoker.get(path, TestRequestHeaders.internal(REQUEST_ID)));
    }

    /** 两个测试 Client 的 Customer 行。 */
    protected final List<Map<String, Object>> customerRows() {
        return customerMapper.customers(clientId(), otherClientId());
    }

    private Response response(EntityExchangeResult<byte[]> result) {
        byte[] bytes = result.getResponseBody();
        return new Response(result, bytes == null || bytes.length == 0 ? null : objectMapper.readTree(bytes));
    }

    /**
     * HTTP 结果
     *
     * @param result 原始交换结果
     * @param body JSON Body；无 Body 时为空
     */
    protected record Response(EntityExchangeResult<byte[]> result, JsonNode body) {
        /** HTTP 状态码。 */
        public int status() {
            return result.getStatus().value();
        }

        /** Body 中的字段文本；缺失或 JSON null 时为空。 */
        public String text(String field) {
            return body == null || body.get(field) == null || body.get(field).isNull() ? null
                : body.get(field).asString();
        }
    }
}
