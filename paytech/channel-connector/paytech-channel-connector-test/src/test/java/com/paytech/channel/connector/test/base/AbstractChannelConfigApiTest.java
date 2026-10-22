package com.paytech.channel.connector.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * 渠道 / 账号 / MID / 能力管理接口测试的公共调用与断言
 * <li>写命令带操作者请求头 {@value ChannelConnectorHeaders#ACTOR_HEADER}，查询不带；响应断言复用渠道成本的通用 Checker。</li>
 * <li>本类不放 {@code @Test}，也不声明 BeforeEach；全局空基线与清理由 ChannelConnectorMockTestBase 与 Foundation TestBase 负责。</li>
 */
public abstract class AbstractChannelConfigApiTest extends ChannelConnectorMockTestBase {
    /** 渠道接口根路径。 */
    protected static final String CHANNEL_PATH = "/channel-connector/v1/channels";

    /** 技术账号接口根路径。 */
    protected static final String ACCOUNT_PATH = "/channel-connector/v1/channel-accounts";

    /** MID 接口根路径。 */
    protected static final String MID_PATH = "/channel-connector/v1/mids";

    /** 能力组接口根路径。 */
    protected static final String CAPABILITY_PATH = "/channel-connector/v1/mid-capabilities";

    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "ChannelConfig.Test_01";

    /** 操作者 Account ID。 */
    protected static final String ACTOR_ID = "91001";

    /** 标准 OE。 */
    protected static final String ENTITY_NO = "AE001";

    /** 合成的 43 字符 Vault Token。 */
    protected static final String TOKEN_A = "A".repeat(39) + "1a2B";

    /** 第二个合成 Vault Token。 */
    protected static final String TOKEN_B = "B".repeat(39) + "9z8Y";

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ChannelTestMapper testMapper;

    /** 以标准操作者发送写命令。 */
    protected ChannelCostResponseChecker command(HttpMethod method, String path, Object body) {
        return command(method, path, ACTOR_ID, body);
    }

    /** 以指定操作者头发送写命令；actor 为空表示不带该请求头。 */
    protected ChannelCostResponseChecker command(HttpMethod method, String path, String actor, Object body) {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        if (actor != null) {
            headers.set(ChannelConnectorHeaders.ACTOR_HEADER, actor);
        }
        return new ChannelCostResponseChecker(http.exchange(method, path, headers, body), objectMapper, REQUEST_ID);
    }

    /** 发送查询。 */
    protected ChannelCostResponseChecker get(String path) {
        return new ChannelCostResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 状态变更请求体。 */
    protected static ChangeChannelStatusRequestBody status(ChannelStatus targetStatus) {
        return status(targetStatus, null);
    }

    /** 带原因的状态变更请求体。 */
    protected static ChangeChannelStatusRequestBody status(ChannelStatus targetStatus, String reason) {
        ChangeChannelStatusRequestBody body = new ChangeChannelStatusRequestBody();
        body.setTargetStatus(targetStatus.getCode());
        body.setReason(reason);
        return body;
    }

    /** 读取对象的变更记录，按主键升序。 */
    protected List<Map<String, Object>> changes(ChangeTargetType targetType, long targetId) {
        return testMapper.selectConfigChanges(targetType.getCode(), targetId);
    }

    /** 断言一条变更记录的类型、状态与操作者。 */
    protected static void assertChange(Map<String, Object> change, String changeType, String fromStatus,
        String toStatus) {
        assertEquals(changeType, change.get("change_type"), "change_type");
        assertEquals(fromStatus, change.get("from_status"), "from_status");
        assertEquals(toStatus, change.get("to_status"), "to_status");
        assertEquals(Long.parseLong(ACTOR_ID), ((Number)change.get("operator_id")).longValue(), "operator_id");
    }

    /** 断言 409 并发冲突。 */
    protected static void assertConflict(ChannelCostResponseChecker checker) {
        checker.assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
    }

    /** 断言 422 字段校验失败，返回同一 Checker 以便继续断言字段位置。 */
    protected static ChannelCostResponseChecker assertValidationError(ChannelCostResponseChecker checker) {
        return checker.assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode());
    }
}
