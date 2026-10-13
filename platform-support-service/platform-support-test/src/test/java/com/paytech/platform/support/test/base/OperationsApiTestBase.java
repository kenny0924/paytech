package com.paytech.platform.support.test.base;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.AuditChecker;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.OperationsTestMapper;

/** Operations 内部入口的公共调用：可信账号与会话 Header 由 Gateway 转发，测试原样发送。 */
public abstract class OperationsApiTestBase extends MockTestBase {

    protected static final String ACTOR = "X-Gateway-Actor-Subject";
    protected static final String SESSION = "X-Gateway-Session-Id";

    @Autowired
    protected OperationsTestMapper operationsData;

    @Autowired
    protected AuditChecker auditChecker;

    /** 本例请求标识；每次调用独立生成，便于按请求定位审计事件。 */
    protected String requestId;

    /** 以人员当前会话构造可信 Header。 */
    protected HttpHeaders headers(Operator operator) {
        return headers(operator.actor(), operator.session());
    }

    /** 原样保留缺失或非法的可信 Header，便于负例。 */
    protected HttpHeaders headers(String actor, String session) {
        requestId = "plt-ops-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        if (actor != null) {
            headers.set(ACTOR, actor);
        }
        if (session != null) {
            headers.set(SESSION, session);
        }
        return headers;
    }

    /** 发送一次真实 HTTP 请求并返回响应 Checker。 */
    protected PlatformResponseChecker call(HttpMethod method, String path, HttpHeaders headers, Object body) {
        String expected = headers.getFirst("X-Request-Id");
        requestId = expected;
        return new PlatformResponseChecker(httpTestInvoker.exchange(method, path, headers, body), objectMapper,
            expected);
    }

    /** 以人员身份 GET。 */
    protected PlatformResponseChecker get(String path, Operator operator) {
        return call(HttpMethod.GET, path, headers(operator), null);
    }

    /** 以人员身份 POST。 */
    protected PlatformResponseChecker post(String path, Operator operator, Object body) {
        return call(HttpMethod.POST, path, headers(operator), body);
    }

    /** 以人员身份 PUT。 */
    protected PlatformResponseChecker put(String path, Operator operator, Object body) {
        return call(HttpMethod.PUT, path, headers(operator), body);
    }

    /** 以人员身份 DELETE。 */
    protected PlatformResponseChecker delete(String path, Operator operator) {
        return call(HttpMethod.DELETE, path, headers(operator), null);
    }

    /** 无可信身份的内部调用。 */
    protected PlatformResponseChecker anonymous(HttpMethod method, String path, Object body) {
        return call(method, path, headers(null, null), body);
    }
}
