package com.paytech.risk.api.decision;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.request.CreateTestReleaseRequestBody;
import com.paytech.risk.api.decision.request.ValidateTestReleaseRequestBody;
import com.paytech.risk.api.decision.response.CreateTestReleaseResponseBody;
import com.paytech.risk.api.decision.response.TestReleaseDetailResponseBody;
import com.paytech.risk.api.decision.response.TestReleaseListItemResponseBody;
import com.paytech.risk.api.decision.response.ValidateTestReleaseResponseBody;

import jakarta.validation.Valid;

/**
 * Risk TEST Candidate 创建、查询与可恢复静态校验的内部 HTTP 合同。 HTTP 请求幂等由 Gateway 负责，校验恢复使用发布身份和原操作事实。
 */
@HttpExchange("/decision-releases/v1")
public interface DecisionReleaseManagementApi {

    /**
     * 按决策点和状态分页查询 TEST Candidate。
     *
     * @param actorAccountId
     *            Gateway 传入的可信操作者 Account ID
     * @param sessionId
     *            Gateway 传入的可信会话标识
     * @param page
     *            页码，缺省为 1，范围为 1–100000
     * @param size
     *            每页条数，缺省为 10，只允许 10、20、50、100、200
     * @param pointCode
     *            可选决策点编码，精确匹配；省略时不按编码筛选
     * @param status
     *            可选 DRAFT、VALIDATING 或 REJECTED，省略时查询全部候选状态
     * @return 按创建时间、发布 ID 降序的分页；总数和条目来自同一只读快照，尚未完成检查时 allStaticChecksPassed 为 null
     */
    @GetExchange("/candidates")
    PagingResponseBody<TestReleaseListItemResponseBody> listCandidates(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "pointCode", required = false) String pointCode,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 查询指定 TEST Candidate 的冻结组成和完整审计事实。
     *
     * @param actorAccountId
     *            Gateway 传入的可信操作者 Account ID
     * @param sessionId
     *            Gateway 传入的可信会话标识
     * @param releaseId
     *            发布 ID，正 BIGINT 十进制字符串
     * @return 同一只读快照中的发布详情、状态审计及零条或完整八项静态检查；未登记返回 404 RISK_DECISION_CONFIGURATION_NOT_FOUND
     */
    @GetExchange("/candidates/{releaseId}")
    TestReleaseDetailResponseBody getCandidate(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("releaseId") String releaseId);

    /**
     * 使用具备旧冻结发布组成的 CHECKOUT_PREPARE 规则版本创建 TEST Candidate。
     *
     * @param actorAccountId
     *            Gateway 传入的可信操作者 Account ID，作为创建人保存
     * @param sessionId
     *            Gateway 传入的可信会话标识
     * @param requestBody
     *            准确的冻结规则版本 ID 及调用方读取的内容哈希；无旧发布组成的规则版本不具备创建资格
     * @return 与初始审计原子保存的 DRAFT/version=1 Candidate；每次有效调用新建候选，预期内容哈希不匹配返回 409 CONCURRENT_REQUEST
     */
    @PostExchange("/candidates")
    CreateTestReleaseResponseBody createCandidate(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateTestReleaseRequestBody requestBody);

    /**
     * 开始或恢复指定 Candidate 的唯一一轮八项静态校验，已完成时返回原检查结果。
     *
     * @param actorAccountId
     *            Gateway 传入的可信操作者 Account ID；恢复或重取结果必须与原开始审计的操作者一致
     * @param sessionId
     *            Gateway 传入的可信会话标识
     * @param releaseId
     *            发布 ID，正 BIGINT 十进制字符串；首次校验要求 DRAFT/version=1
     * @param requestBody
     *            原校验开始前的 expectedVersion，固定为 1；中断恢复和结果重取仍使用原值
     * @return HTTP 200 及固定顺序的八项结果；全通过保持 VALIDATING/version=2，有失败则为 REJECTED/version=3；版本或原操作者不匹配返回 409
     *         CONCURRENT_REQUEST
     */
    @PostExchange("/candidates/{releaseId}/validate")
    ValidateTestReleaseResponseBody validateCandidate(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("releaseId") String releaseId,
        @Valid @RequestBody ValidateTestReleaseRequestBody requestBody);
}
