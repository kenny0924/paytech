package com.paytech.commercial.test.base.checkout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.domain.CheckoutResolutionContext;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.integration.ClientMasterCheckoutClient;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.checker.checkout.CheckoutResolutionResponseChecker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.json.JsonMapper;

/** 仅保留完整解析的 HTTP 路径、请求模板和公共调用器，不包含测试或业务数据准备。 */
public abstract class AbstractCheckoutApiTest extends CommercialMockTestBase {
    protected static final String PATH = "/checkout-context/v1/resolutions";
    protected static final String REQUEST_ID = "Cct.Resolution_21";

    @Autowired
    protected HttpTestInvoker http;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected ClientMasterCheckoutClient clientMasterClient;
    @Autowired
    protected CheckoutQueryService service;

    /** 每次构造独立的合法请求，具体用例显式修改自己的差异字段。 */
    protected ResolveCheckoutContextRequestBody request() {
        var request = new ResolveCheckoutContextRequestBody();
        request.setClientId(Long.toString(CheckoutFixture.CLIENT_ID));
        request.setOrderNo("ORDER-1");
        request.setConsumerCountryCode("AE");
        request.setCurrency("USD");
        request.setLocale("en-AE");
        request.setContextAsOfTime(CheckoutFixture.NOW.toString());
        return request;
    }

    /** 每次提供独立调用头，不加入请求幂等或自动修复负例。 */
    protected HttpHeaders headers() {
        var headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set("X-Caller-Service", "acquiring-service");
        return headers;
    }

    /** 发送实际 HTTP 请求并复用 Foundation 的协议断言。 */
    protected CheckoutResolutionResponseChecker invoke(Object body, HttpHeaders headers) {
        return check(http.post(PATH, headers, body));
    }

    /** 路径差异只供查询参数负例使用，Body 和 Header 原样发送。 */
    protected CheckoutResolutionResponseChecker invoke(Object body, String suffix, HttpHeaders headers) {
        return check(http.post(PATH + suffix, headers, body));
    }

    /** 将公共调用器的原始结果交给一次性的业务响应检查器。 */
    protected CheckoutResolutionResponseChecker check(
            org.springframework.test.web.servlet.client.EntityExchangeResult<byte[]> response) {
        return new CheckoutResolutionResponseChecker(response, json, REQUEST_ID);
    }

    /**
     * 经正式出站端口与本库只读 Service 构造解析上下文，与 Processor 的加载阶段相同（client-master 候选 → 本库快照 → 参与方资料）。
     */
    protected CheckoutResolutionContext context(CheckoutQuery query) {
        var master = clientMasterClient.candidates(query);
        var facts = service.load(master.scope());
        return new CheckoutResolutionContext(query, master, facts, clientMasterClient.partyProfiles(facts.partyProfileIds()));
    }
}
