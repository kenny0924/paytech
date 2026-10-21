package com.paytech.channel.connector.test.base;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.ValidationIssueDetail;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.domain.execution.JsonValues;
import com.paytech.channel.connector.protocol.domain.execution.StandardApiNotSentException;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.checker.ExternalApiResponseChecker;
import com.paytech.channel.connector.test.dal.ExternalApiTestMapper;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 外部 API 管理接口与标准 API 进程内执行的公共调用（2026-10-20 自 Platform 迁入）
 * <li>写命令（创建、修改、删除与发起验证运行）带操作者请求头 {@value ChannelConnectorHeaders#ACTOR_HEADER}；查询与 4 个校验接口不带。</li>
 * <li>Platform 原有的会话、权限复核与审计已由本 Owner 去除：权限归 Gateway / BFF，留痕改为同事务写入 t_channel_config_change。</li>
 * <li>标准 API 执行的入口为 protocol 公开的 {@link ExecuteStandardApiProcessor}，原 HTTP 执行接口已删除。</li>
 * <li>本类不放 {@code @Test}；各测试类在自己的 BeforeEach 中准备配置数据。</li>
 */
public abstract class ExternalApiTestBase extends ChannelConnectorMockTestBase {

    /** 管理接口路径前缀。 */
    protected static final String BASE = "/external-api-admin/v1";

    /** 写命令的标准操作者 Account ID（规范正十进制）。 */
    protected static final String ACTOR = "91002";

    @Autowired
    protected ExternalApiMocker externalApi;

    @Autowired
    protected ExternalApiTestMapper externalApiData;

    @Autowired
    protected HttpTestInvoker httpTestInvoker;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ExecuteStandardApiProcessor standardApiExecutor;

    /** 本例最近一次请求的 Request ID。 */
    protected String requestId;

    /**
     * 构造内部调用 Header，原样保留缺失或非法的操作者，便于负例
     *
     * @param actor 操作者请求头取值；null 表示不带该请求头
     * @return 每次独立生成 Request ID 的 Header
     */
    protected HttpHeaders headers(String actor) {
        requestId = "cc-ext-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        if (actor != null) {
            headers.set(ChannelConnectorHeaders.ACTOR_HEADER, actor);
        }
        return headers;
    }

    /**
     * 发送一次真实 HTTP 请求并返回响应 Checker
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param actor 操作者请求头取值；null 表示不带
     * @param body 请求体，可为空
     * @return 响应 Checker
     */
    protected ExternalApiResponseChecker call(HttpMethod method, String path, String actor, Object body) {
        HttpHeaders headers = headers(actor);
        return new ExternalApiResponseChecker(httpTestInvoker.exchange(method, path, headers, body), objectMapper,
            headers.getFirst("X-Request-Id"));
    }

    /** 查询：不带操作者。 */
    protected ExternalApiResponseChecker get(String path) {
        return call(HttpMethod.GET, path, null, null);
    }

    /** 以标准操作者 POST 写命令。 */
    protected ExternalApiResponseChecker post(String path, Object body) {
        return call(HttpMethod.POST, path, ACTOR, body);
    }

    /** 以标准操作者 PUT 写命令。 */
    protected ExternalApiResponseChecker put(String path, Object body) {
        return call(HttpMethod.PUT, path, ACTOR, body);
    }

    /** 以标准操作者发送带请求体的 DELETE。 */
    protected ExternalApiResponseChecker delete(String path, Object body) {
        return call(HttpMethod.DELETE, path, ACTOR, body);
    }

    /** 静态校验接口：POST 且不带操作者。 */
    protected ExternalApiResponseChecker validate(String path, Object body) {
        return call(HttpMethod.POST, path, null, body);
    }

    /** 写成功并返回写结果，检查对象类型、编码与新版本。 */
    protected static ExternalApiMutationResponseBody assertMutation(ExternalApiResponseChecker checker,
        String objectType, String objectCode, int version) {
        var mutation = checker.assertOk().as(ExternalApiMutationResponseBody.class);
        assertAll(() -> assertEquals(objectType, mutation.getObjectType()),
            () -> assertEquals(objectCode, mutation.getObjectCode()),
            () -> assertEquals(version, mutation.getVersion()));
        return mutation;
    }

    /** 校验接口返回的问题编码与位置，按“编码@位置”排序比较。 */
    protected static List<String> issues(ExternalApiResponseChecker checker) {
        return checker.assertOk().as(ValidationResponseBody.class).getIssues().stream()
            .map(ExternalApiTestBase::issue).sorted().toList();
    }

    /** 单个问题的“编码@位置”。 */
    protected static String issue(ValidationIssueDetail issue) {
        return issue.getCode() + "@" + issue.getLocation();
    }

    /**
     * 断言对象恰有一条指定类型的变更记录（原 Platform 审计改为 t_channel_config_change）
     * <li>核对主键、操作者、原因与 change_detail 中的对象编码和前后版本；返回 change_detail 供用例补充断言。</li>
     *
     * @param targetType 被变更对象类型
     * @param targetId 对象主键
     * @param changeType 变更类型
     * @param objectCode 对象编码，下级对象为 {接入方编码}/{对象编码}
     * @param beforeVersion 变更前版本，创建为 0
     * @param afterVersion 变更后版本；删除为删除前版本
     * @param reason 请求填写的原因
     * @return change_detail JSON
     */
    protected JsonNode assertChange(ChangeTargetType targetType, long targetId, ChangeType changeType,
        String objectCode, int beforeVersion, int afterVersion, String reason) {
        List<Map<String, Object>> rows = externalApiData.changes(targetType.getCode(), targetId).stream()
            .filter(row -> changeType.getCode().equals(row.get("change_type"))).toList();
        assertEquals(1, rows.size(), "变更记录条数：" + targetType.getCode() + "/" + changeType.getCode());
        Map<String, Object> row = rows.getFirst();
        JsonNode detail = objectMapper.readTree(String.valueOf(row.get("change_detail")));
        assertAll(() -> assertEquals(Long.parseLong(ACTOR), ((Number)row.get("operator_id")).longValue(), "operator_id"),
            () -> assertEquals(reason, row.get("reason"), "reason"),
            () -> assertNull(row.get("from_status"), "from_status"), () -> assertNull(row.get("to_status"), "to_status"),
            () -> assertEquals(objectCode, detail.path("objectCode").asString(), "objectCode"),
            () -> assertEquals(beforeVersion, detail.path("beforeVersion").asInt(), "beforeVersion"),
            () -> assertEquals(afterVersion, detail.path("afterVersion").asInt(), "afterVersion"),
            () -> assertTrue(detail.path("changes").isArray(), "changes"));
        return detail;
    }

    /** change_detail 中出现的字段名，保持写入顺序。 */
    protected static List<String> changedFields(JsonNode detail) {
        return StreamSupport.stream(detail.path("changes").spliterator(), false).map(change -> change.path("field").asString()).toList();
    }

    /**
     * 经 protocol 公开的进程内入口执行标准 API（原 Platform 内部执行接口第 1～3 节）
     *
     * @param standardApiCode 标准 API 编码
     * @param integrationCode 接入方编码
     * @param input 标准 API 输入
     * @return 执行结果
     */
    protected ExecutionResult execute(String standardApiCode, String integrationCode, JsonNode input) {
        return standardApiExecutor.process(new ExecuteStandardApiRequest(standardApiCode, integrationCode, input));
    }

    /** 执行结果的输出，按原内部接口的 JSON 口径（十进制写为字符串）转换。 */
    protected static JsonNode outputs(ExecutionResult result) {
        return JsonValues.toJson(result.getOutputs());
    }

    /**
     * 断言执行在第一个节点前以未发出失败结束（原 Platform 内部接口的 NOT_SENT Problem）
     *
     * @param reason 期望的未发出原因
     * @param execution 执行动作
     * @return 捕获的异常，供补充断言字段位置
     */
    protected static StandardApiNotSentException assertNotSent(StandardApiNotSentCode reason, Executable execution) {
        var failure = assertThrows(StandardApiNotSentException.class, execution);
        assertAll(() -> assertEquals(reason, failure.getReason()),
            () -> assertEquals(reason.getCode(), failure.getCode()));
        return failure;
    }
}
