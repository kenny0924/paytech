package com.paytech.risk.test.checker.decision;

import static com.paytech.risk.test.checker.decision.FlowGraphChecker.assertFields;
import static com.paytech.risk.test.checker.decision.FlowGraphChecker.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.decision.domain.enums.FlowStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 整体删除流程的 HTTP 响应 Checker
 *
 * @author Kenny
 */
public final class DeleteFlowResponseChecker extends AbstractHttpResponseChecker<DeleteFlowResponseChecker> {

    public DeleteFlowResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected DeleteFlowResponseChecker self() {
        return this;
    }

    /** 成功响应：原流程身份、DELETED、修订号加一、删除人；返回请求窗口内的删除时间。 */
    public Instant assertDeleted(long flowId, long version, long deletedBy, Instant from, Instant to) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "flowId", "status", "version", "deletedBy", "deletedTime");
        assertAll("删除结果", () -> assertEquals(Long.toString(flowId), text(body.path("flowId")), "flowId"),
            () -> assertEquals(FlowStatus.DELETED.getCode(), text(body.path("status")), "status"),
            () -> assertEquals(Long.toString(version), text(body.path("version")), "version"),
            () -> assertEquals(Long.toString(deletedBy), text(body.path("deletedBy")), "deletedBy"));
        return FlowGraphChecker.assertTimeWithin(text(body.path("deletedTime")), from, to, "deletedTime");
    }

    /** Problem 响应的业务 Code 属于给定集合之一，其余协议字段按统一 Problem 合同核对。 */
    public DeleteFlowResponseChecker assertProblemIn(int status, String... codes) {
        String actual = text(body().path("code"));
        assertTrue(List.of(codes).contains(actual), "Problem code 不在期望集合内：" + actual);
        return assertProblem(status, actual);
    }
}
