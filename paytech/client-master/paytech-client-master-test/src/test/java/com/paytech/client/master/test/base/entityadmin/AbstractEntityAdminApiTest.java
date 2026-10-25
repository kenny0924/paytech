package com.paytech.client.master.test.base.entityadmin;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.client.master.test.base.checkout.AbstractCheckoutCandidateApiTest;
import com.paytech.client.master.test.checker.checkout.CheckoutCandidateResponseChecker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.node.ObjectNode;

/** OE / Local Partner 运营接口的公共路径、Gateway 操作者头与调用器；每例先插入候选合成主档事实。 */
public abstract class AbstractEntityAdminApiTest extends AbstractCheckoutCandidateApiTest {
    protected static final String ADMIN = "/client-master-admin/v1";
    protected static final String ADMIN_REQUEST_ID = "ClientMaster.EntityAdmin_01";
    /** 运营操作者。 */
    protected static final long OPERATOR_ID = 9_300_000_019L;
    /** 夹具中由 PayFac 式 Partner 提供的两个 OE 与只做推荐的 Partner。 */
    protected static final long PARTNER_ONE = 910000000000150L;
    protected static final long PARTNER_TWO = 910000000000250L;
    protected static final long REFERRER_ONLY = 910000000000350L;

    @Autowired
    protected RedissonClient redisson;

    /** Gateway 可信操作者头。 */
    protected HttpHeaders adminHeaders() {
        var headers = TestRequestHeaders.internal(ADMIN_REQUEST_ID);
        headers.set("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID));
        headers.set("X-Gateway-Session-Id", "ops-session-1");
        return headers;
    }

    /** 运营 GET。 */
    protected CheckoutCandidateResponseChecker adminGet(String path) {
        return new CheckoutCandidateResponseChecker(invoker.get(ADMIN + path, adminHeaders()), json, ADMIN_REQUEST_ID);
    }

    /** 运营 PUT。 */
    protected CheckoutCandidateResponseChecker adminPut(String path, Object body) {
        return new CheckoutCandidateResponseChecker(invoker.exchange(HttpMethod.PUT, ADMIN + path, adminHeaders(), body), json,
            ADMIN_REQUEST_ID);
    }

    /** 收单资质修改请求。 */
    protected ObjectNode qualification(String value, long expectedVersion) {
        return json.createObjectNode().put("acquiringQualification", value).put("expectedChangeVersion", expectedVersion)
            .put("reason", "Acquirer approved programme");
    }

    /** 角色替换请求。 */
    protected ObjectNode roles(long expectedVersion, String... roles) {
        ObjectNode body = json.createObjectNode().put("expectedChangeVersion", expectedVersion).put("reason", "Partner contract signed");
        var array = body.putArray("roles");
        for (String role : roles) {
            array.add(role);
        }
        return body;
    }

    /** 测试线程持有指定资源锁期间执行调用，返回各次结果。 */
    protected List<CheckoutCandidateResponseChecker> holding(String key, Supplier<List<CheckoutCandidateResponseChecker>> calls) {
        RLock held = redisson.getLock(key);
        try {
            if (!held.tryLock(0, 30, TimeUnit.SECONDS)) {
                throw new AssertionError("测试无法取得资源锁: " + key);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        try {
            return calls.get();
        } finally {
            held.unlock();
        }
    }
}
