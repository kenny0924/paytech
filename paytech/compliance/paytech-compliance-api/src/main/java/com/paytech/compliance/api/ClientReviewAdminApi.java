package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.api.request.ClientReviewReturnRequestBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;

import jakarta.validation.Valid;

/**
 * Operations Client 入驻审核（O01、O02 Risk 部分、O05、O07—O09）的私网合同；由 Operations BFF 调用
 * <li>操作者来自 Gateway 可信头；菜单与路由权限由 Gateway/BFF 绑定，Risk 只记录操作人。</li>
 * <li>命令按任务状态防重：同一结果重放返回当前结果，不同结果 409；不读取 Idempotency-Key。</li>
 */
@HttpExchange("/client-onboarding-admin/v1")
public interface ClientReviewAdminApi {

    /**
     * O01 审核任务列表，按提交时间倒序
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param page
     *            页码，缺省 1
     * @param size
     *            每页条数，缺省 20，最大 100
     * @param status
     *            可选主任务状态
     * @param qualificationType
     *            可选资格类型
     * @param applicationId
     *            可选 CCT 申请 ID
     * @return 主任务摘要分页
     */
    @GetExchange("/cases")
    PagingResponseBody<ClientReviewCaseResponseBody> listCases(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "qualificationType", required = false) String qualificationType,
        @RequestParam(value = "applicationId", required = false) String applicationId);

    /**
     * O02 Risk 部分：主任务、关联人员子任务、材料、证据、已接收分类与通过门禁
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param caseId
     *            主任务 ID
     * @return 任务详情；不存在返回 404
     */
    @GetExchange("/cases/{caseId}")
    ClientReviewCaseResponseBody getCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId);

    /**
     * O05 核验一份材料
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param caseId
     *            主任务 ID
     * @param evidenceId
     *            证据 ID，必须属于该主任务或其子任务
     * @param request
     *            核验结果
     * @return 命令结果
     *         <li>任务已结束 409 CLIENT_REVIEW_CLOSED；已核验为其他结果 409 CLIENT_REVIEW_EVIDENCE_REVIEWED。</li>
     */
    @PostExchange("/cases/{caseId}/evidence/{evidenceId}/review")
    ClientReviewCommandResponseBody reviewEvidence(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @PathVariable("evidenceId") String evidenceId,
        @Valid @RequestBody ClientReviewEvidenceReviewRequestBody request);

    /**
     * O07 退回修改：结束主任务与仍在进行的子任务，结果送回 CCT
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param caseId
     *            主任务 ID
     * @param request
     *            原因、分区与客户可见说明
     * @return 命令结果；已以其他结果结束 409 CLIENT_REVIEW_CLOSED
     */
    @PostExchange("/cases/{caseId}/return")
    ClientReviewCommandResponseBody returnCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewReturnRequestBody request);

    /**
     * O08 通过：门禁满足后主任务与全部子任务一起结束为 APPROVED
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param caseId
     *            主任务 ID
     * @param request
     *            原因与可选客户可见说明
     * @return 命令结果；门禁未满足 422 CLIENT_REVIEW_NOT_READY
     */
    @PostExchange("/cases/{caseId}/approval")
    ClientReviewCommandResponseBody approveCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewDecisionRequestBody request);

    /**
     * O09 最终拒绝：主任务 REJECTED，仍在进行的子任务随之撤销
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param caseId
     *            主任务 ID
     * @param request
     *            原因与可选客户可见说明
     * @return 命令结果
     */
    @PostExchange("/cases/{caseId}/rejection")
    ClientReviewCommandResponseBody rejectCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewDecisionRequestBody request);
}
