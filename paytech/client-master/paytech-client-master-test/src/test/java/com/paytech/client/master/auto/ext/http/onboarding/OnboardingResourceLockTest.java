package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.PartyRelationType;
import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.PersonFixture;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * 申请写入口的资源锁互斥域：申请、账号未完成名额与人员三类 Key 被占用时，对应写入口在等待上限后返回并发冲突且不写数据；只读导航不加锁。
 * <li>Key 沿用原注解锁的 {@code prefix:key}，滚动发布期间新旧实例对同一资源互斥。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi
 * @see com.paytech.client.contract.api.onboarding.ClientDocumentApi
 * @see com.paytech.client.contract.api.onboarding.ClientOnboardingStartApi
 */
class OnboardingResourceLockTest extends AbstractSubmissionApiTest {
    /** 申请级写入共用的互斥域前缀。 */
    private static final String APPLICATION_LOCK = "client-onboarding:application:";

    /** 账号未完成申请名额的互斥域前缀。 */
    private static final String ACCOUNT_SLOT_LOCK = "client-onboarding:account-slot:";

    /** 人员（Party）级写入的互斥域前缀。 */
    private static final String PARTY_LOCK = "cct:party:";

    @Autowired
    private RedissonClient redisson;

    private ApplicationFixture app;

    private PersonFixture person;

    /** 本类公共数据：一份组织申请及其一名 UBO 关联人员。 */
    @BeforeEach
    void setUpLockFixtures() {
        app = applications.organization(ACCOUNT_ID);
        person = applications.person(app, PartyRelationType.UBO.getCode());
    }

    /** 申请锁被占用：草稿、人员、关系与材料的写入全部返回 409，申请与关系、材料保持不变。 */
    @Test
    void test_applicationLockContention() {
        long documentId = availableDocument(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        Instant updatedAt = mapper.applicationUpdateTime(app.applicationId());

        holding(APPLICATION_LOCK + app.applicationId(), () -> List.of(
            call("PATCH", "/{id}/draft", "{\"dirtyFields\":[],\"values\":{}}", app.applicationId()),
            call("POST", "/{id}/people", "{\"person\":{\"dirtyFields\":[],\"values\":{}},\"relation\":{}}", app.applicationId()),
            call("PATCH", "/{id}/relations/{relationId}", "{\"dirtyFields\":[],\"values\":{}}", app.applicationId(),
                person.relationId()),
            call("DELETE", "/{id}/relations/{relationId}", null, app.applicationId(), person.relationId()),
            call("POST", "/{id}/documents", "{\"subjectPartyId\":\"" + app.partyId()
                + "\",\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"fileAddress\":\"/files/v1/1\"}",
                app.applicationId()),
            call("DELETE", "/{id}/documents/{documentId}", null, app.applicationId(), documentId)));

        assertEquals(updatedAt, mapper.applicationUpdateTime(app.applicationId()));
        assertEquals(app.applicationId(), mapper.relationApplication(person.relationId()));
        assertEquals(1, mapper.documentCount(app.applicationId()));
    }

    /** 人员锁被占用：同时需要申请锁与人员锁的保存人员、新建关系返回 409，关系不新增。 */
    @Test
    void test_partyLockContention() {
        holding(PARTY_LOCK + person.partyId(), () -> List.of(
            call("PATCH", "/{id}/people/{partyId}", "{\"dirtyFields\":[],\"values\":{}}", app.applicationId(),
                person.partyId()),
            call("POST", "/{id}/relations", "{\"relatedPartyId\":\"" + person.partyId()
                + "\",\"relationType\":\"UBO\",\"ownershipPercentage\":\"40\",\"effectiveFrom\":\"2024-01-01\"}",
                app.applicationId())));

        assertEquals(app.applicationId(), mapper.relationApplication(person.relationId()));
    }

    /** 账号名额锁被占用：开始新申请返回 409，不分配申请。 */
    @Test
    void test_accountSlotLockContention() {
        long accountId = applications.nextId();

        holding(ACCOUNT_SLOT_LOCK + accountId, () -> List.of(http.post().uri("/client-onboarding/v1/applications")
            .headers(headers -> client(headers, accountId)).header("Content-Type", "application/json")
            .body("{\"clientType\":\"" + ClientType.ORGANIZATION.getCode() + "\"}")
            .exchange().expectBody(byte[].class).returnResult()));
    }

    /** 只读导航不加锁：账号名额锁被占用时开始页查询仍返回 200。 */
    @Test
    void test_startNotLocked() {
        RLock held = redisson.getLock(ACCOUNT_SLOT_LOCK + ACCOUNT_ID);
        assertTrue(tryHold(held));
        try {
            var start = http.get().uri("/client-onboarding/v1/start").headers(headers -> client(headers, ACCOUNT_ID))
                .exchange().expectBody(byte[].class).returnResult();
            assertEquals(200, start.getStatus().value(), new String(start.getResponseBody()));
            assertEquals(Long.toString(app.applicationId()), body(start).path("applications").path(0).path("applicationId").asString());
        } finally {
            held.unlock();
        }
    }

    /** 在测试线程持有指定 Key 期间依次调用写入口，每次都必须是并发冲突。 */
    void holding(String key, Supplier<List<EntityExchangeResult<byte[]>>> calls) {
        RLock held = redisson.getLock(key);
        assertTrue(tryHold(held));
        try {
            for (EntityExchangeResult<byte[]> result : calls.get()) {
                assertEquals(409, result.getStatus().value(), new String(result.getResponseBody()));
                assertEquals(CommonErrorCode.CONCURRENT_REQUEST.getCode(), body(result).path("code").asString());
            }
        } finally {
            held.unlock();
        }
    }

    /** 立即取得锁，租约覆盖整段调用。 */
    boolean tryHold(RLock lock) {
        try {
            return lock.tryLock(0, 30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    /** 以默认账号调用申请子资源。 */
    EntityExchangeResult<byte[]> call(String method, String path, String body, Object... variables) {
        var request = http.method(org.springframework.http.HttpMethod.valueOf(method))
            .uri("/client-onboarding/v1/applications" + path, variables).headers(headers -> client(headers, ACCOUNT_ID));
        if (body != null) {
            request.header("Content-Type", "application/json").body(body);
        }
        return request.exchange().expectBody(byte[].class).returnResult();
    }

    /** 可信 Client BFF 调用头。 */
    void client(HttpHeaders headers, long accountId) {
        headers.set("X-Caller-Service", "client-console-bff");
        headers.set("X-Gateway-Actor-Subject", Long.toString(accountId));
        headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
        headers.set("X-Request-Id", "cct-resource-lock-test");
    }
}
