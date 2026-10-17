package com.paytech.commercial.auto.ext.http.agreement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commercial.api.agreement.AgreementPartyQueryApi;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * client-master 人员共享标记使用的合同参与方引用查询（design/06 §2.2）。
 * @see AgreementPartyQueryApi#exists(String, List, List)
 */
class AgreementPartyQueryHttpTest extends CommercialMockTestBase {
    private static final String PATH = "/commercial/v1/agreement-parties/exists";
    private static final String REQUEST_ID = "Commercial.AgreementParty_04";

    @Autowired
    HttpTestInvoker http;
    @Autowired
    JsonMapper json;

    /** 每例插入含合同参与方的商务夹具。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture.install();
    }

    /** 只返回被引用的 Party 与资料，未引用的 ID 不返回。 */
    @Test
    void test_referencedPartiesAndProfiles() {
        var result = http.get(PATH + "?partyIds=910000000000002&partyIds=1&partyProfileIds=910000000000111&partyProfileIds=2",
            headers("client-master-service"));
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        JsonNode body = json.readTree(result.getResponseBody());
        assertEquals(List.of("910000000000002"), texts(body.path("referencedPartyIds")));
        assertEquals(List.of("910000000000111"), texts(body.path("referencedPartyProfileIds")));
    }

    /** 非 client-master 调用与缺少 ID 都在读取前拒绝。 */
    @Test
    void test_invalidCallerAndMissingIds() {
        assertInvalid(http.get(PATH + "?partyIds=910000000000002", headers("acquiring-service")));
        assertInvalid(http.get(PATH, headers("client-master-service")));
        assertInvalid(http.get(PATH + "?partyIds=01", headers("client-master-service")));
    }

    /** 400 INVALID_REQUEST。 */
    private void assertInvalid(EntityExchangeResult<byte[]> result) {
        assertEquals(400, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals(CommonErrorCode.INVALID_REQUEST.getCode(), json.readTree(result.getResponseBody()).path("code").asString());
    }

    /** 私网调用头。 */
    private static HttpHeaders headers(String caller) {
        var headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set("X-Caller-Service", caller);
        return headers;
    }

    /** 文本数组。 */
    private static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
