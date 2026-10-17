package com.paytech.risk.api.decision;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.api.decision.request.StartShadowRunRequestBody;
import com.paytech.risk.api.decision.response.ExecutionReleaseListItemResponseBody;
import com.paytech.risk.api.decision.response.ExecutionReleaseResponseBody;

import jakarta.validation.Valid;

/**
 * 冻结决策流的执行发布：创建、静态校验、影子运行与 TEST 激活的内部 HTTP 合同。
 * 一期只在 TEST 环境单人激活；PRODUCTION 双人审批未开放。HTTP 请求幂等由 Gateway 负责。
 */
@HttpExchange("/decision-releases/v1")
public interface ExecutionReleaseManagementApi {

    /**
     * 创建执行发布
     *
     * @param actorAccountId
     *            Gateway 传入的可信操作者 Account ID，作为创建人保存
     * @param sessionId
     *            Gateway 传入的可信会话标识
     * @param requestBody
     *            决策点与决策流版本
     * @return DRAFT/version=1 的 TEST 发布；每次有效调用新建发布。决策点不支持执行发布或流程不属于该点返回 400
     */
    @PostExchange("/executions")
    ExecutionReleaseResponseBody createExecution(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateExecutionReleaseRequestBody requestBody);

    /**
     * 分页查询执行发布
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param page 页码，缺省为 1，范围为 1–100000
     * @param size 每页条数，缺省为 10，只允许 10、20、50、100、200
     * @param pointCode 决策点编码，必填；未登记返回 400
     * @param status 可选发布状态编码，未知返回 400；ACTIVE 即当前生效发布
     * @return 按创建时间、发布 ID 倒序的分页；只含经决策流的执行发布，不含结账测试候选。
     *         <li>已替代的发布不能重新激活；回滚以其 flowVersionId 新建执行发布并走校验、影子运行、激活。</li>
     */
    @GetExchange("/executions")
    PagingResponseBody<ExecutionReleaseListItemResponseBody> listExecutions(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "pointCode", required = false) String pointCode,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 查询执行发布
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param releaseId 发布 ID
     * @return 发布、静态校验与最近一次影子运行；不存在或为结账测试候选返回 404
     */
    @GetExchange("/executions/{releaseId}")
    ExecutionReleaseResponseBody getExecution(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("releaseId") String releaseId);

    /**
     * 执行一轮静态校验
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param releaseId 发布 ID
     * @return 全部通过进入 SHADOWING，否则进入 REJECTED；已校验的发布返回原结果
     */
    @PostExchange("/executions/{releaseId}/validate")
    ExecutionReleaseResponseBody validateExecution(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("releaseId") String releaseId);

    /**
     * 启动影子运行：回放当前生效发布最近的成功正式评估
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param releaseId 候选发布 ID，须为 SHADOWING
     * @param requestBody 目标样本数
     * @return 发布及运行中的批次；回放异步执行，完成后可查询汇总。没有生效发布或已有运行中批次返回 400
     */
    @PostExchange("/executions/{releaseId}/shadow-runs")
    ExecutionReleaseResponseBody startShadowRun(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("releaseId") String releaseId,
        @Valid @RequestBody StartShadowRunRequestBody requestBody);

    /**
     * 激活 TEST 执行发布
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，同时记录为审批人
     * @param sessionId Gateway 传入的可信会话标识
     * @param releaseId 候选发布 ID，须为 SHADOWING
     * @return ACTIVE 发布；已有生效发布时必须先完成以其为基线且技术门槛通过的影子运行，否则返回 400
     */
    @PostExchange("/executions/{releaseId}/activate")
    ExecutionReleaseResponseBody activateExecution(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("releaseId") String releaseId);
}
