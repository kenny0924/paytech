package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;

/**
 * 参考数据快照不可用（ReferenceDataUnavailableException）时 C14、提交核对与 O03 的结果：503 ONBOARDING_DEPENDENCY_UNAVAILABLE，不是 422，不写入
 * <li>独立上下文只开启 CURRENCY、COUNTRY、INDUSTRY：行政区与 MCC 的读法抛"参考数据未配置"，与"未就绪"是同一异常类型、同一转换路径；
 * 共享上下文的快照一旦加载就无法回到未就绪。类结束即关闭本上下文，避免两个上下文同时消费本服务分区队列。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientClassificationAdminApi
 */
@TestPropertySource(properties = "platform-support.reference-data.client.kinds=CURRENCY,COUNTRY,INDUSTRY")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReferenceDataUnavailableTest extends AbstractClientReviewTest {

    /** 申请地址填了行政区 AE-DU：核对与 C14 都是 503，申请保持草稿、无提交与审核登记。 */
    @Test
    void test_submissionUnavailable() {
        owners.mockStandardPrograms();
        var app = applications.individual(ACCOUNT_ID);

        var check = call(app, false);
        var result = call(app, true);

        assertEquals(503, check.getStatus().value(), new String(check.getResponseBody()));
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(check).path("code").asString());
        assertEquals(503, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(result).path("code").asString());
        assertEquals("DRAFT", mapper.application(app.applicationId()).get("status"));
        assertNull(mapper.application(app.applicationId()).get("current_submission_id"));
        assertEquals(0, mapper.reviewEventCount());
        assertEquals(0, owners.count("review:"));
    }

    /** O03 校验 MCC 时快照不可用：503，不写分类。 */
    @Test
    void test_classificationUnavailable() {
        var review = applications.underReview(ACCOUNT_ID);

        var unavailable = replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true)));

        assertEquals(503, unavailable.getStatus().value(), new String(unavailable.getResponseBody()));
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(unavailable).path("code").asString());
        assertEquals(0, mapper.classifications(review.app().clientId()).size());
    }

    /** 以 client-console-bff 身份调用 C14（submit=true）或提交核对。 */
    EntityExchangeResult<byte[]> call(ApplicationFixture app, boolean submit) {
        var request = submit
            ? http.post().uri("/client-onboarding/v1/applications/{id}/submissions", app.applicationId())
            : http.get().uri("/client-onboarding/v1/applications/{id}/submission-check", app.applicationId());
        return request.header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-reference-data-test")
            .exchange().expectBody(byte[].class).returnResult();
    }
}
