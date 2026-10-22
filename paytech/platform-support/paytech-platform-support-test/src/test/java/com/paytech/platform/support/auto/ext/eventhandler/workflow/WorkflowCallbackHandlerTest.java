package com.paytech.platform.support.auto.ext.eventhandler.workflow;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.workflow.event.WorkflowCallbackRaisedPayload;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.test.base.WorkflowApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.workflow.WorkflowEventProbe;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.service.handler.WorkflowCallbackEventHandler;
import com.paytech.platform.support.workflow.service.handler.WorkflowNotificationHandler;

import tools.jackson.databind.JsonNode;

/**
 * 工作流通知回调的提交后投递：CALLBACK 经本地 RabbitMQ 发布 WorkflowCallbackRaised，NOTIFY 经 Delivery 受理邮件；
 * 失败计次、到上限置 FAILED 结束任务，人工重试重新登记任务。执行全部走正式补偿 Handler，到期调整只改任务时间。
 *
 * @see WorkflowCallbackEventHandler
 * @see WorkflowNotificationHandler
 */
class WorkflowCallbackHandlerTest extends WorkflowApiTestBase {

    @Autowired
    WorkflowEventProbe probe;

    @Autowired
    CompensationEventScanner scanner;

    private Operator executor;
    private Operator approver;
    private long confirm;

    /** 本类公共：执行人、审批人、人工确认模板与本例临时事件队列。 */
    @BeforeEach
    void setUpCallbackMocks() {
        executor = person();
        approver = person();
        confirm = confirmTemplate("CONFIRM_SETUP");
        probe.bind();
    }

    /** 节点放行与实例完成的业务回调在提交后发布，记录置 SENT，任务删除；信封冻结状态变化、操作人与实例版本。 */
    @Test
    void test_callbackPublished() {
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核")
            .action("confirm", confirm, executor.accountId(), true).approval("ANY", approver.accountId())
            .callback("PASSED", "PRODUCT_READY").end("end").chain("start", "review", "end")
            .instanceCallback("COMPLETED", "ONBOARDING_DONE").build();
        long definitionId = published("CALLBACK_FLOW", graph, WorkflowGraphJson.emptyForm());
        long instanceId = started(definitionId, admin, WorkflowGraphJson.empty());
        confirm(instanceId, "confirm", executor);
        approve(instanceId, approver);

        JsonNode first = probe.awaitEnvelope(instanceId);
        JsonNode second = probe.awaitEnvelope(instanceId);
        workflowFixture.awaitIdle();

        Map<String, JsonNode> payloads = List.of(first, second).stream().collect(Collectors.toMap(
            envelope -> envelope.path("payload").path("callbackCode").asString(), envelope -> envelope));
        JsonNode passed = payloads.get("PRODUCT_READY");
        List<Map<String, Object>> callbacks = workflowData.callbacks(instanceId);
        assertAll(() -> assertEquals(Set.of("PRODUCT_READY", "ONBOARDING_DONE"), payloads.keySet()),
            () -> assertEquals(WorkflowCallbackRaisedPayload.EVENT_TYPE, passed.path("eventType").asString()),
            () -> assertEquals(WorkflowCallbackRaisedPayload.AGGREGATE_TYPE, passed.path("aggregateType").asString()),
            () -> assertEquals("PASSED", passed.path("payload").path("trigger").asString()),
            () -> assertEquals("review", passed.path("payload").path("nodeKey").asString()),
            () -> assertEquals("PASSED", passed.path("payload").path("toStatus").asString()),
            () -> assertEquals(approver.actor(), passed.path("payload").path("actorId").asString()),
            () -> assertEquals("BK-" + definitionId, passed.path("payload").path("businessKey").asString()),
            () -> assertEquals("workflow-callback:" + passed.path("payload").path("callbackId").asString(),
                passed.path("eventId").asString()),
            () -> assertEquals("COMPLETED", payloads.get("ONBOARDING_DONE").path("payload").path("trigger").asString()),
            () -> assertEquals(List.of("SENT", "SENT"), callbacks.stream().map(row -> row.get("status")).toList()),
            () -> assertTrue(workflowData.events().isEmpty()));
    }

    /** 邮件受理失败计次；到上限置 FAILED 并结束任务；人工重试重新登记；只有实例管理权限可以重试，非 FAILED 不能重试。 */
    @Test
    void test_notificationFailedAndRetried() {
        JsonNode graph = WorkflowGraphJson.graph().task("review", "审核")
            .action("confirm", confirm, executor.accountId(), true).approval("ANY", approver.accountId())
            .notify("ACTIVATED", "NODE_EXECUTORS").end("end").chain("start", "review", "end").build();
        long instanceId = started(published("NOTIFY_FLOW", graph, WorkflowGraphJson.emptyForm()), admin,
            WorkflowGraphJson.empty());
        workflowFixture.awaitIdle();
        Map<String, Object> firstAttempt = workflowData.callbacks(instanceId).get(0);
        String callbackId = Long.toString(((Number) firstAttempt.get("callback_id")).longValue());
        String firstError = (String) firstAttempt.get("last_error");

        post(RUNTIME + "/callbacks/" + callbackId + "/retry", admin, null).assertProblem(400,
            CommonErrorCode.BUSINESS_CHECK_FAILED);
        workflowData.due();
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(WorkflowNotificationHandler.EVENT_TYPE)));
        workflowFixture.awaitIdle();
        Map<String, Object> exhausted = workflowData.callbacks(instanceId).get(0);
        var eventsAfterExhausted = workflowData.events();
        long role = operationsMocker.customRole("WORKFLOW_HANDLER", WorkflowPermissions.TASK_HANDLE);
        Operator handler = operationsMocker.operator(OperatorBuilder.administrator().roles(role));
        post(RUNTIME + "/callbacks/" + callbackId + "/retry", handler, null).assertProblem(403,
            OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        var reopened = post(RUNTIME + "/callbacks/" + callbackId + "/retry", admin, null).assertOk()
            .as(WorkflowMutationResponseBody.class);
        workflowFixture.awaitIdle();
        var activities = get(RUNTIME + "/instances/" + instanceId + "/activities", admin).assertOk()
            .as(WorkflowActivityListResponseBody.class);

        assertAll(() -> assertEquals("NOTIFY", firstAttempt.get("callback_type")),
            () -> assertEquals("PENDING", firstAttempt.get("status")),
            () -> assertEquals(1, ((Number) firstAttempt.get("attempt_count")).intValue()),
            () -> assertTrue(firstError.startsWith("WORKFLOW_NOTIFICATION_FAILED: 通知受理失败："
                + CommonErrorCode.BUSINESS_CHECK_FAILED.getCode() + " "), firstError),
            () -> assertFalse(firstError.contains(executor.email()), firstError),
            () -> assertEquals("FAILED", exhausted.get("status")),
            () -> assertEquals(2, ((Number) exhausted.get("attempt_count")).intValue()),
            () -> assertTrue(eventsAfterExhausted.isEmpty()), () -> assertEquals("PENDING", reopened.getStatus()),
            () -> assertEquals(1, ((Number) workflowData.callbacks(instanceId).get(0).get("attempt_count")).intValue()),
            () -> assertTrue(loginEmailVault.revealCount() >= 3, "每次尝试 Reveal 执行人邮箱"),
            () -> assertTrue(activities.getItems().stream().anyMatch(item -> "NOTIFY".equals(item.getActivityType()))),
            () -> assertTrue(((String) exhausted.get("target")).contains(executor.actor())));
    }
}
