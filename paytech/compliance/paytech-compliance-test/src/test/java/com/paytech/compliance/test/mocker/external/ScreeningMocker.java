package com.paytech.compliance.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.api.serviceexecution.response.ServiceChannelExecutionResponseBody;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * channel-connector SCREENING_SEARCH 的外部 Mocker：按检索全名配置应答（无命中、命中、FAIL、ERROR 或 HTTP 故障），记录每次调用
 * <li>未配置全名时使用本例的缺省应答；连缺省应答也未配置时记为意外调用。全局或测试类不预设成功。</li>
 */
@TestComponent
public class ScreeningMocker implements Clear {
    /** Mock 响应的 Request ID。 */
    public static final String REQUEST_ID = "compliance-external-test";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Object> answers = new HashMap<>();

    private final List<Call> calls = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    private Object defaultAnswer;

    /**
     * 一次调用的关键参数（不含命中原文）
     *
     * @param channelCode 渠道
     * @param standardApiCode 标准 API
     * @param caller 调用方头
     * @param subjectType 主体类型
     * @param fullName 全名
     * @param birthYear 出生年份
     * @param screeningType 筛查类型
     * @param clientRef 请求引用
     * @param countries 检索国家
     */
    public record Call(String channelCode, String standardApiCode, String caller, String subjectType, String fullName,
        String birthYear, String screeningType, String clientRef, java.util.List<String> countries) {
    }

    /** 本例未单独配置的全名都返回无命中。 */
    public synchronized void mockNoHitsByDefault() {
        defaultAnswer = success();
    }

    /** 本例未单独配置的全名都返回该故障。 */
    public synchronized void mockDefaultFault(MockHttpFault fault) {
        defaultAnswer = fault;
    }

    /** 本例未单独配置的全名都返回该执行状态（FAIL 或 ERROR），输出为空对象。 */
    public synchronized void mockDefaultStatus(String executeStatus) {
        defaultAnswer = status(executeStatus);
    }

    /** 该全名在指定筛查类型下返回一条命中，其他类型无命中。 */
    public synchronized void mockHit(String fullName, String screeningType, String externalRef, String listType) {
        answers.put(fullName + "|" + screeningType, hit(externalRef, listType));
    }

    /** 记录调用并返回应答；故障抛出。 */
    public synchronized ServiceChannelExecutionResponseBody answer(String caller, ExecuteServiceChannelRequestBody request) {
        var input = request.getInput();
        String type = input.path("screeningTypes").path(0).asString();
        Call call = new Call(request.getChannelCode(), request.getStandardApiCode(), caller, input.path("subjectType").asString(),
            input.path("fullName").asString(), input.has("birthYear") ? input.path("birthYear").asString() : null, type,
            input.path("clientRef").asString(), input.path("countries").valueStream().map(node -> node.asString()).toList());
        calls.add(call);
        Object answer = answers.getOrDefault(call.fullName() + "|" + type, defaultAnswer);
        if (answer == null) {
            throw unexpectedCall("SCREENING_SEARCH " + call.fullName() + " " + type);
        }
        if (answer instanceof MockHttpFault fault) {
            throw fault;
        }
        return (ServiceChannelExecutionResponseBody)answer;
    }

    /** 记录一次不在配置范围内的调用。 */
    public synchronized MockHttpFault unexpectedCall(String operation) {
        unexpected.add(operation);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    /** 全部调用记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 某全名的调用记录。 */
    public synchronized List<Call> callsFor(String fullName) {
        return calls.stream().filter(call -> call.fullName().equals(fullName)).toList();
    }

    /** 全局 AfterEach：没有意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "存在未配置的 channel-connector 调用");
    }

    /** 全局 BeforeEach：空基线。 */
    public synchronized void assertEmpty() {
        assertAll("ScreeningMocker 应处于空基线", () -> assertTrue(answers.isEmpty()), () -> assertNull(defaultAnswer),
            () -> assertTrue(calls.isEmpty()), () -> assertTrue(unexpected.isEmpty()));
    }

    /** 每例清除。 */
    @Override
    public synchronized void clear() {
        answers.clear();
        calls.clear();
        unexpected.clear();
        defaultAnswer = null;
    }

    /** 第一方 Problem 故障。 */
    public static MockHttpFault problem(HttpStatus status, String code) {
        return MockHttpFault.json(status.value(), new ApiProblemResponse(ApiProblemProfile.TYPE, status.getReasonPhrase(), "合成故障",
            code, REQUEST_ID, "2026-10-27T00:00:00.000000Z", null));
    }

    /** SUCCESS 无命中。 */
    static ServiceChannelExecutionResponseBody success() {
        ObjectNode outputs = JSON.createObjectNode();
        outputs.put("runRef", "mock-run");
        outputs.putArray("hits");
        return response("SUCCESS", outputs);
    }

    /** SUCCESS 一条命中。 */
    static ServiceChannelExecutionResponseBody hit(String externalRef, String listType) {
        ObjectNode outputs = JSON.createObjectNode();
        outputs.put("runRef", "mock-run");
        ArrayNode hits = outputs.putArray("hits");
        ObjectNode hit = hits.addObject();
        hit.put("externalRef", externalRef);
        hit.put("entityType", "PERSON");
        hit.putArray("listTypes").add(listType);
        hit.put("score", "92.5");
        hit.putArray("matchTypes").add("NAME_FUZZY");
        hit.put("riskLevel", "HIGH");
        hit.put("name", "SYNTHETIC LIST ENTRY");
        return response("SUCCESS", outputs);
    }

    /** FAIL / ERROR，输出为空对象。 */
    static ServiceChannelExecutionResponseBody status(String executeStatus) {
        return response(executeStatus, JSON.createObjectNode());
    }

    /** 组装响应。 */
    static ServiceChannelExecutionResponseBody response(String executeStatus, ObjectNode outputs) {
        ServiceChannelExecutionResponseBody body = new ServiceChannelExecutionResponseBody();
        body.setExecuteStatus(executeStatus);
        body.setOutputs(outputs);
        return body;
    }
}
