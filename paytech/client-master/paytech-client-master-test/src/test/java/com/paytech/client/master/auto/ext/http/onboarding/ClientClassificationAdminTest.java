package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.service.handler.ClientClassificationHandler;
import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.client.master.test.mocker.platform.ReferenceDataMocker.Catalog;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * O03/O04 及分类读取：真实 HTTP 进入，行业与 MCC 经真实出站客户端向 Platform 参考数据测试接口校验，分类与确认真实落库，
 * 确认后经补偿任务交给 Risk I02 测试接口。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientClassificationAdminApi
 */
class ClientClassificationAdminTest extends AbstractClientReviewTest {

    /** 设置、替换、确认并交付 Risk；确认重放不重复交付；确认后不能再改。行业与 MCC 每次保存各查询 Platform 一次。 */
    @Test
    void test_normalFlow() {
        var review = applications.underReview(ACCOUNT_ID);
        owners.mockClassificationsReceived();
        assertEquals(0, body(classifications(review.caseId())).path("items").size());

        assertEquals(200, replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true))).getStatus().value());
        var replaced = replace(review.caseId(), List.of(item("SAAS_SUBSCRIPTION", "5734", true), item("RETAIL_GROCERY", "5411", false)));
        assertEquals(200, replaced.getStatus().value());
        assertFalse(body(replaced).path("confirmed").asBoolean());
        assertEquals(2, mapper.classifications(review.app().clientId()).size());
        assertEquals("SAAS_SUBSCRIPTION,RETAIL_GROCERY", referenceData.calls(Catalog.INDUSTRY).getLast().codes());
        assertEquals("5734,5411", referenceData.calls(Catalog.MCC).getLast().codes());
        assertEquals(2, referenceData.calls(Catalog.INDUSTRY).size());

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
        assertEquals("CLASSIFICATION_CONFIRMED",
            body(replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true)))).path("code").asString());
        assertEquals(1, owners.count("classifications:"));
    }

    /** 两个主要分类、MCC 格式、未设置即确认 422；未知任务 404；均不落库，且输入校验失败时不查询 Platform。 */
    @Test
    void test_invalidParameter() {
        var review = applications.underReview(ACCOUNT_ID);

        assertEquals(422, replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true), item("SAAS_SUBSCRIPTION", "5734", true)))
            .getStatus().value());
        assertEquals(422, replace(review.caseId(), List.of(item("RETAIL_GROCERY", "54A1", true))).getStatus().value());
        assertEquals("CLASSIFICATION_INVALID", body(confirm(review.caseId())).path("code").asString());
        assertEquals(404, replace(applications.nextId(), List.of(item("RETAIL_GROCERY", "5411", true))).getStatus().value());

        assertEquals(0, referenceData.count());
        assertEquals(0, mapper.classifications(review.app().clientId()).size());
        assertNull(mapper.classificationProfile(review.app().clientId()).get("classification_confirmed_time"));
        assertEquals(0, owners.count("classifications:"));
    }

    /** 只选大类、行业不存在或已停用：422 CLASSIFICATION_INVALID，errors[] 逐行指向 industryCode 并区分原因，不落库。 */
    @Test
    void test_industryNotDetail() {
        var review = applications.underReview(ACCOUNT_ID);
        referenceData.mockIndustry("RETAIL_GENERAL", 2, ReferenceDataStatus.INACTIVE);

        var rejected = replace(review.caseId(), List.of(item("RETAIL", "5411", true), item("GAMBLING", "5399", false),
            item("RETAIL_GENERAL", "7372", false), item("RETAIL_GROCERY", "5734", false)));

        assertEquals(422, rejected.getStatus().value());
        assertEquals("CLASSIFICATION_INVALID", body(rejected).path("code").asString());
        assertEquals(List.of("items[0].industryCode=须选择细类，RETAIL 是大类", "items[1].industryCode=行业不存在: GAMBLING",
            "items[2].industryCode=行业未启用: RETAIL_GENERAL"), fieldErrors(rejected));
        assertEquals(0, mapper.classifications(review.app().clientId()).size());
    }

    /** MCC 不存在或已停用：422 CLASSIFICATION_INVALID，errors[] 逐行指向 mccCode，不落库。 */
    @Test
    void test_mccNotInCatalog() {
        var review = applications.underReview(ACCOUNT_ID);
        referenceData.mock(Catalog.MCC, "7372", ReferenceDataStatus.INACTIVE);

        var rejected = replace(review.caseId(), List.of(item("RETAIL_GROCERY", "9999", true), item("SAAS_SUBSCRIPTION", "7372", false)));

        assertEquals(422, rejected.getStatus().value());
        assertEquals("CLASSIFICATION_INVALID", body(rejected).path("code").asString());
        assertEquals(List.of("items[0].mccCode=MCC 不存在: 9999", "items[1].mccCode=MCC 未启用: 7372"), fieldErrors(rejected));
        assertEquals(0, mapper.classifications(review.app().clientId()).size());
    }

    /** Problem errors[]，每项为 field=message。 */
    static List<String> fieldErrors(org.springframework.test.web.servlet.client.EntityExchangeResult<byte[]> result) {
        return body(result).path("errors").valueStream()
            .map(error -> error.path("field").asString() + "=" + error.path("message").asString()).toList();
    }

    /** Platform 参考数据不可用：503 ONBOARDING_DEPENDENCY_UNAVAILABLE，已有分类保持不变。 */
    @Test
    void test_platformUnavailable() {
        var review = applications.underReview(ACCOUNT_ID);
        assertEquals(200, replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true))).getStatus().value());
        referenceData.mockUnavailable(Catalog.MCC);

        var unavailable = replace(review.caseId(), List.of(item("SAAS_SUBSCRIPTION", "5734", true)));

        assertEquals(503, unavailable.getStatus().value());
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(unavailable).path("code").asString());
        var rows = mapper.classifications(review.app().clientId());
        assertEquals(1, rows.size());
        assertEquals("RETAIL_GROCERY", rows.getFirst().get("industry_code"));
    }

    /** 迁移前保存的一级行业仍可读取：GET 不向 Platform 校验。 */
    @Test
    void test_legacyIndustryReadable() {
        var review = applications.underReview(ACCOUNT_ID);
        assertEquals(200, replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true))).getStatus().value());
        mapper.setClassificationIndustry(review.app().clientId(), "RETAIL");
        long calls = referenceData.count();

        var current = classifications(review.caseId());

        assertEquals(200, current.getStatus().value());
        assertEquals("RETAIL", body(current).path("items").get(0).path("industryCode").asString());
        assertEquals(calls, referenceData.count());
    }
}
