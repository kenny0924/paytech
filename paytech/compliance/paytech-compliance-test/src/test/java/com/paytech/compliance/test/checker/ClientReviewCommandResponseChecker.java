package com.paytech.risk.test.checker.compliance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.api.compliance.request.ClientReviewRegistrationRequestBody;
import com.paytech.risk.api.enums.ClientReviewErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Client 入驻审核写接口（I01 登记、I02 分类、O05 核验、O07—O09 结论）的 HTTP 响应 Checker
 * <li>协议、Request ID 与 Problem 断言复用 Foundation 基类；这里核对登记结果与命令结果的全部字段。</li>
 *
 * @author Kenny
 */
public final class ClientReviewCommandResponseChecker
    extends AbstractHttpResponseChecker<ClientReviewCommandResponseChecker> {

    public ClientReviewCommandResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected ClientReviewCommandResponseChecker self() {
        return this;
    }

    /**
     * I01 登记成功：只返回来源提交编号与十进制主任务 ID
     *
     * @param request 登记请求
     * @return 主任务 ID
     */
    public long assertRegistered(ClientReviewRegistrationRequestBody request) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("sourceSubmissionId", "caseId"), Set.copyOf(body.propertyNames())),
            () -> assertEquals(request.getSourceSubmissionId(), body.path("sourceSubmissionId").asString()),
            () -> assertTrue(body.path("caseId").isString() && body.path("caseId").asString().matches("[1-9][0-9]{0,18}"),
                "caseId 必须为十进制字符串"));
        return Long.parseLong(body.path("caseId").asString());
    }

    /** I01 重放返回原主任务。 */
    public ClientReviewCommandResponseChecker assertRegistered(ClientReviewRegistrationRequestBody request, long caseId) {
        assertEquals(caseId, assertRegistered(request), "重放应返回原主任务");
        return this;
    }

    /** 命令同步完成：主任务坐标与状态，accepted=true、processing=false。 */
    public ClientReviewCommandResponseChecker assertAccepted(long caseId, String status) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        JsonNode body = body();
        assertAll(() -> assertEquals(Set.of("caseId", "status", "accepted", "processing"), Set.copyOf(body.propertyNames())),
            () -> assertEquals(Long.toString(caseId), body.path("caseId").asString()),
            () -> assertEquals(status, body.path("status").asString()),
            () -> assertTrue(body.path("accepted").isBoolean() && body.path("accepted").asBoolean(), "accepted"),
            () -> assertTrue(body.path("processing").isBoolean() && !body.path("processing").asBoolean(), "processing"));
        return this;
    }

    /** 审核业务失败：状态由 Risk 错误映射决定。 */
    public ClientReviewCommandResponseChecker assertReviewError(int status, ClientReviewErrorCode code) {
        return assertProblem(status, code.getCode());
    }

    /** Problem detail 含给定片段。 */
    public ClientReviewCommandResponseChecker assertDetailContains(String... fragments) {
        String detail = body().path("detail").asString();
        for (String fragment : fragments) {
            assertTrue(detail.contains(fragment), "Problem detail 缺少 " + fragment + "：" + detail);
        }
        return this;
    }
}
