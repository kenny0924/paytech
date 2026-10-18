package com.paytech.client.master.auto.ext.mq.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.ext.mq.ClientReviewResultListener;
import com.paytech.client.master.service.handler.ClientTenantProjectionHandler;
import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;

/**
 * compliance 审核结果消费：测试作为 compliance 以 paytech-messaging 发布到真实本机 RabbitMQ 的 compliance.events，经 client-master
 * 分区队列、消费幂等拦截器与 Handler，申请、Client、生命周期历史真实落库，通过后经补偿任务投影到 Platform 测试接口。
 *
 * @see ClientReviewResultListener#consume
 */
class ClientReviewResultListenerTest extends AbstractClientReviewTest {
    /** 通过：申请 APPROVED 并释放名额，Client ACTIVE，主体资料写入批准时间（等于结论时间、重复投递不变），追加第二版生命周期历史，Platform 收到 ACTIVE 投影；重复投递不重复执行。 */
    @Test
    void test_normalFlow() throws Exception {
        var review = applications.underReview(ACCOUNT_ID);
        owners.mockTenantProjection(review.app().clientId(), ACCOUNT_ID, review.firstPayloadHash());
        long decisionId = applications.nextId();

        publishResult(review, review.caseId(), "APPROVED", decisionId, OPERATOR_ID);

        waitUntil(() -> owners.projectionRequests().size() == 1 && mapper.events(ClientTenantProjectionHandler.EVENT_TYPE,
            Long.toString(changeId(review.app().clientId()))).isEmpty(), Duration.ofSeconds(10));
        var application = mapper.review(review.app().applicationId());
        assertEquals("APPROVED", application.get("status"));
        assertNull(application.get("active_slot"));
        assertEquals(decisionId, ((Number)application.get("qualification_decision_id")).longValue());
        assertEquals("ACTIVE", mapper.clientStatus(review.app().clientId()));
        Instant approvedTime = mapper.profileApprovedTime(review.app().clientId());
        assertEquals(mapper.applicationUpdateTime(review.app().applicationId()), approvedTime);
        var history = mapper.clientHistory(review.app().clientId());
        assertEquals(2, history.size());
        assertEquals("UPDATE", history.get(1).get("operation_type"));
        assertEquals(OPERATOR_ID, ((Number)history.get(1).get("create_by")).longValue());
        var projection = owners.projectionRequests().getFirst();
        assertEquals("ACTIVE", projection.getOwnerStatus());
        assertEquals(2, projection.getSourceVersion());

        double replayed = complianceEvents.outcome("replayed");
        publishResult(review, review.caseId(), "APPROVED", decisionId, OPERATOR_ID);
        complianceEvents.awaitOutcome("replayed", replayed, 1);
        var marker = applications.underReview(ACCOUNT_ID + 1);
        publishResult(marker, marker.caseId(), "RETURNED", null, null);
        waitUntil(() -> "RETURNED".equals(mapper.review(marker.app().applicationId()).get("status")), Duration.ofSeconds(10));
        assertEquals(2, mapper.clientHistory(review.app().clientId()).size());
        assertEquals(1, owners.projectionRequests().size());
        assertEquals(approvedTime, mapper.profileApprovedTime(review.app().clientId()));
    }

    /** 重放跳过：同一信封再次投递由消费登记确认跳过，Handler 不再执行，申请状态与历史不变。 */
    @Test
    void test_replayedEventSkipped() throws Exception {
        var review = applications.underReview(ACCOUNT_ID);
        publishResult(review, review.caseId(), "RETURNED", null, null);
        waitUntil(() -> "RETURNED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        var application = mapper.review(review.app().applicationId());
        double replayed = complianceEvents.outcome("replayed");
        double handled = complianceEvents.outcome("handled");

        publishResult(review, review.caseId(), "RETURNED", null, null);

        complianceEvents.awaitOutcome("replayed", replayed, 1);
        assertEquals(handled, complianceEvents.outcome("handled"));
        assertEquals(application, mapper.review(review.app().applicationId()));
        assertEquals(1, mapper.clientHistory(review.app().clientId()).size());
        assertNull(complianceEvents.receiveDeadLetter(200));
    }

    /** 退回：申请 RETURNED 并保留名额，Client 仍为 PENDING；之后不能再设置本任务分类。 */
    @Test
    void test_returned() throws Exception {
        var review = applications.underReview(ACCOUNT_ID);

        publishResult(review, review.caseId(), "RETURNED", null, null);

        waitUntil(() -> "RETURNED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        assertEquals(Boolean.TRUE, mapper.review(review.app().applicationId()).get("active_slot"));
        assertEquals("PENDING", mapper.clientStatus(review.app().clientId()));
        assertNull(mapper.profileApprovedTime(review.app().clientId()));
        assertEquals("APPLICATION_NOT_EDITABLE", body(replace(review.caseId(), List.of(item("RETAIL", "5411", true)))).path("code").asString());
    }

    /** 最终拒绝：申请 REJECTED 并释放名额，记录决定；Client 不激活、不调用 Platform。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        var review = applications.underReview(ACCOUNT_ID);
        long decisionId = applications.nextId();

        publishResult(review, review.caseId(), "REJECTED", decisionId, OPERATOR_ID);

        waitUntil(() -> "REJECTED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        assertNull(mapper.review(review.app().applicationId()).get("active_slot"));
        assertEquals(decisionId, ((Number)mapper.review(review.app().applicationId()).get("qualification_decision_id")).longValue());
        assertEquals("PENDING", mapper.clientStatus(review.app().clientId()));
        assertNull(mapper.profileApprovedTime(review.app().clientId()));
        assertEquals(1, mapper.clientHistory(review.app().clientId()).size());
    }

    /** 旧任务的迟到结果被确认并忽略；摘要被篡改的消息进入死信队列；两者都不改变申请。 */
    @Test
    void test_edgeCase() throws Exception {
        var review = applications.underReview(ACCOUNT_ID);

        publishResult(review, applications.nextId(), "APPROVED", applications.nextId(), OPERATOR_ID);
        publishTampered(review, review.caseId(), "REJECTED", applications.nextId(), OPERATOR_ID);
        var marker = applications.underReview(ACCOUNT_ID + 1);
        publishResult(marker, marker.caseId(), "RETURNED", null, null);

        waitUntil(() -> "RETURNED".equals(mapper.review(marker.app().applicationId()).get("status")), Duration.ofSeconds(10));
        assertEquals("UNDER_REVIEW", mapper.review(review.app().applicationId()).get("status"));
        assertNotNull(complianceEvents.receiveDeadLetter(5000));
        assertNull(complianceEvents.receiveDeadLetter(200));
    }

    /** 第二版生命周期历史的 change_version_id。 */
    long changeId(long clientId) {
        return mapper.clientChangeId(clientId, 2);
    }
}
