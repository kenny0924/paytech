package com.paytech.counterparty.test.base.customer;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.counterparty.test.base.CounterpartyMockTestBase;
import com.paytech.counterparty.test.dal.CustomerTestMapper;
import com.paytech.counterparty.test.mocker.external.StoredCardMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/** Customer 与已存卡接口共用的真实 HTTP 调用；不包含测试方法或新增生命周期。 */
public abstract class AbstractCustomerApiTest extends CounterpartyMockTestBase {
    private static final String BASE = "/client-gateway-api/v1/customers";

    @Autowired
    protected CustomerTestMapper customerMapper;

    @Autowired
    protected StoredCardMocker storedCards;

    /** 当前运行隔离的主 Client。 */
    protected final long clientId() {
        return customerDataMocker.clientId();
    }

    /** 当前运行隔离的另一个 Client。 */
    protected final long otherClientId() {
        return customerDataMocker.otherClientId();
    }

    /** 以主 Client 创建 Customer。 */
    protected final Response create(String consumerReference) {
        return create(clientId(), consumerReference);
    }

    /** 以指定 Client 创建 Customer。 */
    protected final Response create(long clientId, String consumerReference) {
        return call(HttpMethod.POST, BASE, clientId, Map.of("consumerReference", consumerReference));
    }

    /** 按公开编号读取。 */
    protected final Response get(long clientId, String customerNo) {
        return call(HttpMethod.GET, path("/{no}", customerNo), clientId, null);
    }

    /** 按引用读取。 */
    protected final Response getByReference(long clientId, String consumerReference) {
        var path = UriComponentsBuilder.fromPath(BASE).queryParam("consumerReference", consumerReference).toUriString();
        return call(HttpMethod.GET, path, clientId, null);
    }

    /** 删除 Customer。 */
    protected final Response delete(long clientId, String customerNo) {
        return call(HttpMethod.DELETE, path("/{no}", customerNo), clientId, null);
    }

    /** 列出已存卡。 */
    protected final Response listCards(long clientId, String customerNo) {
        return call(HttpMethod.GET, path("/{no}/stored-cards", customerNo), clientId, null);
    }

    /** 删除已存卡。 */
    protected final Response deleteCard(long clientId, String customerNo, String storedCardToken) {
        var path = UriComponentsBuilder.fromPath(BASE + "/{no}/stored-cards/{token}")
            .buildAndExpand(customerNo, storedCardToken).toUriString();
        return call(HttpMethod.DELETE, path, clientId, null);
    }

    /** 两个测试 Client 的 Customer 行。 */
    protected final List<Map<String, Object>> customerRows() {
        return customerMapper.customers(clientId(), otherClientId());
    }

    private String path(String suffix, String customerNo) {
        return UriComponentsBuilder.fromPath(BASE + suffix).buildAndExpand(customerNo).toUriString();
    }

    private Response call(HttpMethod method, String path, long clientId, Object body) {
        var headers = TestRequestHeaders.client(REQUEST_ID, Long.toString(clientId), "1");
        var result = httpTestInvoker.exchange(method, path, headers, body);
        return new Response(result, read(result));
    }

    private JsonNode read(EntityExchangeResult<byte[]> result) {
        byte[] bytes = result.getResponseBody();
        return bytes == null || bytes.length == 0 ? null : objectMapper.readTree(bytes);
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

        /** Body 中的字段文本。 */
        public String text(String field) {
            return body == null || body.get(field) == null || body.get(field).isNull() ? null
                : body.get(field).asString();
        }
    }
}
