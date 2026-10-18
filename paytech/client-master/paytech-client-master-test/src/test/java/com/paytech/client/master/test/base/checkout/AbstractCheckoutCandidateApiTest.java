package com.paytech.client.master.test.base.checkout;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody;
import com.paytech.client.master.test.base.ClientMasterMockTestBase;
import com.paytech.client.master.test.checker.checkout.CheckoutCandidateResponseChecker;
import com.paytech.client.master.test.dal.checkout.CheckoutCandidateTestMapper;
import com.paytech.client.master.test.mocker.checkout.CheckoutCandidateFixture;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.json.JsonMapper;

/** 两段式解析主档接口的公共路径、请求模板与调用器；每例先插入合成主档事实。 */
public abstract class AbstractCheckoutCandidateApiTest extends ClientMasterMockTestBase {
    protected static final String CANDIDATES = "/client-master/v1/checkout-candidates";
    protected static final String VERIFY = "/client-master/v1/checkout-candidates/verify";
    protected static final String PROFILES = "/client-master/v1/party-profiles";
    protected static final String REQUEST_ID = "ClientMaster.Candidates_04";

    @Autowired
    protected HttpTestInvoker invoker;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected CheckoutCandidateFixture fixture;
    @Autowired
    protected CheckoutCandidateTestMapper data;

    /** 当前类公共准备：完整合成主档事实。 */
    @BeforeEach
    void setUpCandidateFixture() {
        fixture.install();
    }

    /** 每次构造独立的合法候选请求。 */
    protected CheckoutCandidatesRequestBody request() {
        var request = new CheckoutCandidatesRequestBody();
        request.setClientId(Long.toString(CheckoutCandidateFixture.CLIENT_ID));
        request.setConsumerCountryCode("AE");
        request.setCurrency("USD");
        request.setLocale("en-AE");
        request.setContextAsOfTime(CheckoutCandidateFixture.NOW.toString());
        return request;
    }

    /** commercial 私网调用头。 */
    protected HttpHeaders headers() {
        var headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set("X-Caller-Service", "commercial-service");
        return headers;
    }

    /** 发送 POST 请求。 */
    protected CheckoutCandidateResponseChecker post(String path, Object body, HttpHeaders headers) {
        return new CheckoutCandidateResponseChecker(invoker.post(path, headers, body), json, REQUEST_ID);
    }

    /** 发送 GET 请求。 */
    protected CheckoutCandidateResponseChecker get(String path, HttpHeaders headers) {
        return new CheckoutCandidateResponseChecker(invoker.get(path, headers), json, REQUEST_ID);
    }
}
