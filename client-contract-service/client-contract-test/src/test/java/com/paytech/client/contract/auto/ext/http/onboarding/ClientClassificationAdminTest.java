package com.paytech.client.contract.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.client.contract.service.handler.ClientClassificationHandler;
import com.paytech.client.contract.test.base.onboarding.AbstractClientReviewTest;

/**
 * O03/O04 及分类读取：真实 HTTP 进入，分类与确认真实落库，确认后经补偿任务交给 Risk I02 测试接口。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientClassificationAdminApi
 */
class ClientClassificationAdminTest extends AbstractClientReviewTest {

    /** 设置、替换、确认并交付 Risk；确认重放不重复交付；确认后不能再改。 */
    @Test
    void test_normalFlow() {
        var review = applications.underReview(ACCOUNT_ID);
        owners.mockClassificationsReceived();
        assertEquals(0, body(classifications(review.caseId())).path("items").size());

        assertEquals(200, replace(review.caseId(), List.of(item("RETAIL", "5411", true))).getStatus().value());
        var replaced = replace(review.caseId(), List.of(item("SOFTWARE_SAAS", "5734", true), item("RETAIL", "5411", false)));
        assertEquals(200, replaced.getStatus().value());
        assertFalse(body(replaced).path("confirmed").asBoolean());
        assertEquals(2, mapper.classifications(review.app().clientId()).size());

        var confirmed = confirm(review.caseId());
        assertEquals(200, confirmed.getStatus().value());
        assertTrue(body(confirmed).path("confirmed").asBoolean());
        assertEquals(Long.toString(OPERATOR_ID), body(confirmed).path("confirmedBy").asString());
        waitUntil(() -> owners.classificationRequests().size() == 1
            && mapper.events(ClientClassificationHandler.EVENT_TYPE, Long.toString(review.caseId())).isEmpty(), Duration.ofSeconds(10));
        var delivered = owners.classificationRequests().getFirst();
        assertEquals(review.submissionId(), delivered.getSubmissionId());
        assertEquals(Long.toString(OPERATOR_ID), delivered.getConfirmedBy());
        assertEquals(body(confirmed).path("confirmedAt").asString(), delivered.getConfirmedAt());
        assertEquals(1, delivered.getClassifications().stream().filter(item -> item.getPrimary()).count());

        var replay = confirm(review.caseId());
        assertEquals(200, replay.getStatus().value());
        assertEquals(body(confirmed).path("confirmedAt").asString(), body(replay).path("confirmedAt").asString());
        assertEquals("CLASSIFICATION_CONFIRMED", body(replace(review.caseId(), List.of(item("RETAIL", "5411", true)))).path("code").asString());
        assertEquals(1, owners.count("classifications:"));
    }

    /** 目录外行业、两个主要分类、MCC 格式、未设置即确认 422；未知任务 404；均不落库。 */
    @Test
    void test_invalidParameter() {
        var review = applications.underReview(ACCOUNT_ID);

        assertEquals(422, replace(review.caseId(), List.of(item("GAMBLING", "7995", true))).getStatus().value());
        assertEquals(422, replace(review.caseId(), List.of(item("RETAIL", "5411", true), item("SOFTWARE_SAAS", "5734", true)))
            .getStatus().value());
        assertEquals(422, replace(review.caseId(), List.of(item("RETAIL", "54A1", true))).getStatus().value());
        assertEquals("CLASSIFICATION_INVALID", body(confirm(review.caseId())).path("code").asString());
        assertEquals(404, replace(applications.nextId(), List.of(item("RETAIL", "5411", true))).getStatus().value());

        assertEquals(0, mapper.classifications(review.app().clientId()).size());
        assertNull(mapper.classificationProfile(review.app().clientId()).get("classification_confirmed_time"));
        assertEquals(0, owners.count("classifications:"));
    }

    /** 行业目录来自 CCT 配置。 */
    @Test
    void test_options() {
        var options = http.get().uri("/client-onboarding-admin/v1/classification-options")
            .header("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID)).header("X-Gateway-Session-Id", "ops-session-1")
            .exchange().expectStatus().isOk().expectBody(byte[].class).returnResult();

        assertEquals(List.of("RETAIL", "SOFTWARE_SAAS"),
            body(options).path("industries").valueStream().map(node -> node.path("code").asString()).toList());
    }
}
