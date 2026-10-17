package com.paytech.platform.support.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.integration.domain.execution.ExecutionTrace;
import com.paytech.platform.support.integration.domain.verify.VerifyAssertion;
import com.paytech.platform.support.integration.domain.verify.VerifyMatch;
import com.paytech.platform.support.integration.domain.verify.VerifyStep;
import com.paytech.platform.support.integration.domain.verify.VerifyStepResult;
import com.paytech.platform.support.integration.domainservice.verify.VerifyAssertionEvaluator;
import com.paytech.platform.support.integration.domainservice.verify.VerifyTemplateRenderer;
import com.paytech.platform.support.test.builder.ExternalApiSamples;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 沙箱验证证据的纯计算：轨迹脱敏与报文截断、输入模板渲染（类型保留、拼接、前序步骤、缺失值）、期望核对。
 * 完整运行链路见 VerifyRunTest；本类缩小这些规则的故障定位范围。
 *
 * @see ExecutionTrace#exchange(String, ExecutionTrace.Exchange, Set, Integer, Map, byte[], long, String)
 * @see VerifyTemplateRenderer#render(JsonNode, VerifyTemplateRenderer.Scope)
 * @see VerifyAssertionEvaluator#evaluate(VerifyStep, VerifyStepResult, String)
 */
class VerifyEvidenceTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final VerifyTemplateRenderer renderer = new VerifyTemplateRenderer();
    private final VerifyAssertionEvaluator evaluator = new VerifyAssertionEvaluator();

    /** 认证类与受控句柄 Header 脱敏，签名 Header 保留；超长报文截断，非文本记 Base64 预览。 */
    @Test
    void test_traceRedactionAndTruncation() {
        var trace = new ExecutionTrace();
        var headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer secret");
        headers.put("X-Api-Key", "secret-2");
        headers.put("sign", "abc==");
        byte[] large = "a".repeat(ExecutionTrace.MAX_BODY_BYTES + 10).getBytes(StandardCharsets.UTF_8);
        trace.exchange("call", new ExecutionTrace.Exchange("POST", URI.create("https://sandbox.test/x"), headers, large),
            Set.of("x-api-key"), 200, Map.of("Set-Cookie", "s=1", "sign", "resp"), new byte[] {(byte)0xff, 0x00},
            12, null);
        trace.nodeResult("call", "CALL", "SUCCESS", null, 15);

        @SuppressWarnings("unchecked")
        var node = ((List<Map<String, Object>>)trace.toMap().get("nodes")).getFirst();
        @SuppressWarnings("unchecked")
        var exchange = (Map<String, Map<String, Object>>)node.get("exchange");
        @SuppressWarnings("unchecked")
        var sent = (Map<String, String>)exchange.get("request").get("headers");
        @SuppressWarnings("unchecked")
        var sentBody = (Map<String, Object>)exchange.get("request").get("body");
        @SuppressWarnings("unchecked")
        var received = (Map<String, String>)exchange.get("response").get("headers");
        @SuppressWarnings("unchecked")
        var receivedBody = (Map<String, Object>)exchange.get("response").get("body");
        assertAll(() -> assertEquals(ExecutionTrace.REDACTED, sent.get("Authorization")),
            () -> assertEquals(ExecutionTrace.REDACTED, sent.get("X-Api-Key")), () -> assertEquals("abc==", sent.get("sign")),
            () -> assertEquals(ExecutionTrace.REDACTED, received.get("Set-Cookie")),
            () -> assertEquals(true, sentBody.get("truncated")),
            () -> assertEquals(ExecutionTrace.MAX_BODY_BYTES, ((String)sentBody.get("text")).length()),
            () -> assertEquals("/wA=", receivedBody.get("base64")), () -> assertEquals("SUCCESS", node.get("executeStatus")));
    }

    /** 整串变量保留类型，嵌在文字中拼接，前序步骤输出可引用；未知变量、缺失值与对象拼接被拒绝。 */
    @Test
    void test_templateRendering() {
        var scope = new VerifyTemplateRenderer.Scope("901", "2026-10-14T00:00:00Z",
            ExternalApiSamples.object("{\"channelCode\":\"DEMO\"}"),
            new LinkedHashMap<>(Map.of("pay", new VerifyTemplateRenderer.StepValues(
                ExternalApiSamples.object("{\"amount\":\"1.00\"}"), ExternalApiSamples.object("{\"ref\":\"r-1\",\"count\":2}")))));
        JsonNode rendered = renderer.render(ExternalApiSamples.object("""
            {"account":"${account.identity}","order":"VR${run.code}","ref":"${steps.pay.outputs.ref}",
             "count":"${steps.pay.outputs.count}","digits":"${random.digits(6)}","list":["${run.time}"],"n":5}"""), scope);

        assertAll(() -> assertEquals("DEMO", rendered.path("account").path("channelCode").asString()),
            () -> assertEquals("VR901", rendered.path("order").asString()), () -> assertEquals("r-1", rendered.path("ref").asString()),
            () -> assertTrue(rendered.path("count").isIntegralNumber()),
            () -> assertTrue(rendered.path("digits").asString().matches("[0-9]{6}")),
            () -> assertEquals("2026-10-14T00:00:00Z", rendered.path("list").get(0).asString()),
            () -> assertEquals(5, rendered.path("n").asInt()));
        for (String template : List.of("${unknown}", "${steps.pay.outputs.missing}", "x${account.identity}",
            "${steps.later.input.a}", "${random.digits(19)}")) {
            assertThrows(IllegalArgumentException.class,
                () -> renderer.render(ExternalApiSamples.object("{\"v\":\"" + template.replace("\"", "\\\"") + "\"}"), scope),
                template);
        }
    }

    /** 期望核对：执行状态、出口、各匹配方式；问题期望；未能执行时只有一条未满足的 execution。 */
    @Test
    void test_assertions() {
        var step = new VerifyStep("pay", "PAY", NODES.objectNode(), "SUCCESS", null, null, "done", List.of(
            output("status", VerifyMatch.EQUALS, NODES.stringNode("PAID")), output("ref", VerifyMatch.EXISTS, null),
            output("note", VerifyMatch.ABSENT, null), output("url", VerifyMatch.REGEX, NODES.stringNode("^https://")),
            output("status", VerifyMatch.ONE_OF, ExternalApiSamples.object("{\"v\":[\"PAID\",\"PENDING\"]}").get("v"))));
        var result = VerifyStepResult.executed("SUCCESS",
            ExternalApiSamples.object("{\"status\":\"PAID\",\"ref\":\"r\",\"url\":\"https://x\"}"));
        List<VerifyAssertion> passed = evaluator.evaluate(step, result, "done");
        assertTrue(VerifyAssertionEvaluator.passed(passed), passed.toString());
        assertEquals(7, passed.size());

        List<VerifyAssertion> wrongExit = evaluator.evaluate(step, result, "other");
        assertEquals(List.of("returnId"), wrongExit.stream().filter(item -> !item.passed()).map(VerifyAssertion::target).toList());

        var problemStep = new VerifyStep("bad", "PAY", NODES.objectNode(), null, 422, "VALIDATION_ERROR", null, List.of());
        assertTrue(VerifyAssertionEvaluator.passed(evaluator.evaluate(problemStep,
            VerifyStepResult.problem(422, "VALIDATION_ERROR", List.of("input.x")), null)));
        assertFalse(VerifyAssertionEvaluator.passed(evaluator.evaluate(problemStep,
            VerifyStepResult.executed("SUCCESS", NODES.objectNode()), null)));

        List<VerifyAssertion> failed = evaluator.evaluate(step, VerifyStepResult.failed("出站地址未标记沙箱"), null);
        assertAll(() -> assertEquals(1, failed.size()), () -> assertEquals("execution", failed.getFirst().target()),
            () -> assertFalse(VerifyAssertionEvaluator.passed(failed)));
    }

    private static VerifyStep.OutputExpectation output(String path, VerifyMatch match, JsonNode value) {
        return new VerifyStep.OutputExpectation(path, match, value);
    }
}
