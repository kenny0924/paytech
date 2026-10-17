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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 保存流程的 HTTP 响应 Checker
 * <li>核对本次版本号、根修订号、保存人与保存时间；服务端生成的流程与版本 ID 返回给用例继续核对落库事实。</li>
 *
 * @author Kenny
 */
public final class SaveFlowResponseChecker extends AbstractHttpResponseChecker<SaveFlowResponseChecker> {

    /** 本次保存产生的身份与时间。 */
    public record Saved(long flowId, long flowVersionId, Instant savedTime) {
    }

    public SaveFlowResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected SaveFlowResponseChecker self() {
        return this;
    }

    /** 成功响应：版本号、根修订号与保存人符合期望，保存时间位于请求窗口内。 */
    public Saved assertSaved(int versionNo, long version, long savedBy, Instant from, Instant to) {
        assertStatus(200);
        JsonNode body = body();
        assertFields(body, "flowId", "flowVersionId", "versionNo", "version", "savedBy", "savedTime");
        assertAll("保存结果", () -> assertTrue(text(body.path("flowId")).matches("[1-9][0-9]{0,18}"), "flowId"),
            () -> assertTrue(text(body.path("flowVersionId")).matches("[1-9][0-9]{0,18}"), "flowVersionId"),
            () -> assertEquals(versionNo, body.path("versionNo").asInt(), "versionNo"),
            () -> assertEquals(Long.toString(version), text(body.path("version")), "version"),
            () -> assertEquals(Long.toString(savedBy), text(body.path("savedBy")), "savedBy"));
        Instant savedTime = FlowGraphChecker.assertTimeWithin(text(body.path("savedTime")), from, to, "savedTime");
        return new Saved(Long.parseLong(text(body.path("flowId"))), Long.parseLong(text(body.path("flowVersionId"))),
            savedTime);
    }

    /** Problem 响应的业务 Code 属于给定集合之一，其余协议字段按统一 Problem 合同核对。 */
    public SaveFlowResponseChecker assertProblemIn(int status, String... codes) {
        String actual = text(body().path("code"));
        assertTrue(List.of(codes).contains(actual), "Problem code 不在期望集合内：" + actual);
        return assertProblem(status, actual);
    }
}
