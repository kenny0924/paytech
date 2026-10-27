package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.compliance.api.request.ClientReviewCheckResultRequestBody;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.api.request.ClientReviewHitResolutionRequestBody;
import com.paytech.compliance.api.request.ClientReviewRecommendationRequestBody;
import com.paytech.compliance.api.request.ClientReviewReturnRequestBody;
import com.paytech.compliance.api.request.ClientReviewRiskRatingRequestBody;
import com.paytech.compliance.api.request.ClientReviewScreeningRunRequestBody;
import com.paytech.compliance.api.request.ClientReviewWaiverRequestBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewProgramListResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * Operations Client 入驻审核工作台的私网合同；由 Operations BFF 调用
 * <li>操作者来自 Gateway 可信头；菜单与路由权限由 Gateway/BFF 绑定，compliance 只记录操作人。</li>
 * <li>命令按案件状态防重：同一结果重放返回当前结果，不同结果 409；不读取 Idempotency-Key。</li>
 * <li>所有写命令可带 onBehalfOfPartnerId（平台运营代责任 PayFac 操作），compliance 记 operated_by=PLATFORM 与该值。</li>
 * <li>首个审核操作把 SUBMITTED 的主案件推进为 IN_REVIEW；已结束案件的写命令返回 409 CLIENT_REVIEW_CLOSED。</li>
 */
@HttpExchange("/client-onboarding-admin/v1")
public interface ClientReviewAdminApi {

    /**
     * O01 审核案件列表，按提交时间倒序
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 20，1—100
     * @param status 可选主案件状态
     * @param qualificationType 可选资格类型
     * @param applicationId 可选申请 ID
     * @param responsiblePartnerId 可选责任 PayFac ID；传 0 表示只看平台自营（责任方为空）
     * @param jurisdictionCode 可选辖区代码
     * @return 主案件摘要分页
     */
    @GetExchange("/cases")
    PagingResponseBody<ClientReviewCaseResponseBody> listCases(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "qualificationType", required = false) String qualificationType,
        @RequestParam(value = "applicationId", required = false) String applicationId,
        @RequestParam(value = "responsiblePartnerId", required = false) String responsiblePartnerId,
        @RequestParam(value = "jurisdictionCode", required = false) String jurisdictionCode);

    /**
     * O14 尽调程序目录（只读）：各辖区程序的全部版本及材料、检查、筛查模板
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param jurisdictionCode 可选辖区代码，精确匹配；不传返回全部（含全局程序）
     * @param status 可选程序状态：DRAFT、ACTIVE、RETIRED
     * @return 程序版本列表；超过 200 个版本返回 400，需加过滤条件
     */
    @GetExchange("/programs")
    ClientReviewProgramListResponseBody listPrograms(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "jurisdictionCode", required = false) String jurisdictionCode,
        @RequestParam(value = "status", required = false) String status);

    /**
     * O02 案件工作台详情：主案件、关联人员子案件、材料、检查、筛查、分类、风险评级、推荐、退回错误、时间线与批准门禁
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID；用于计算四眼门禁
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @return 案件详情；不存在返回 404
     */
    @GetExchange("/cases/{caseId}")
    ClientReviewCaseResponseBody getCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId);

    /**
     * O05 核验一份材料
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param evidenceId 证据 ID，必须属于该主案件或其子案件
     * @param request 核验结果；拒绝必须带原因码
     * @return 命令结果
     *         <li>已核验为其他结果 409 CLIENT_REVIEW_EVIDENCE_REVIEWED；拒绝缺原因码或所属要求已豁免 400。</li>
     */
    @PostExchange("/cases/{caseId}/evidence/{evidenceId}/review")
    ClientReviewCommandResponseBody reviewEvidence(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @PathVariable("evidenceId") String evidenceId,
        @Valid @RequestBody ClientReviewEvidenceReviewRequestBody request);

    /**
     * 豁免一项可豁免的材料要求
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param requirementId 要求 ID，必须属于该主案件或其子案件
     * @param request 原因码与说明
     * @return 命令结果
     *         <li>程序不允许豁免或要求已 ACCEPTED 返回 400 BUSINESS_CHECK_FAILED；已豁免直接返回。</li>
     */
    @PostExchange("/cases/{caseId}/requirements/{requirementId}/waiver")
    ClientReviewCommandResponseBody waiveRequirement(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @PathVariable("requirementId") String requirementId, @Valid @RequestBody ClientReviewWaiverRequestBody request);

    /**
     * 录入一项人工检查的结论
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param checkId 检查 ID，必须属于该主案件或其子案件
     * @param request 结论、原因码、引用与备注
     * @return 命令结果
     *         <li>同结论重放直接返回；已录入其他结论 409 CLIENT_REVIEW_CHECK_RECORDED；非人工检查 400。</li>
     */
    @PostExchange("/cases/{caseId}/checks/{checkId}/result")
    ClientReviewCommandResponseBody recordCheckResult(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @PathVariable("checkId") String checkId, @Valid @RequestBody ClientReviewCheckResultRequestBody request);

    /**
     * 重新发起案件（主案件与子案件）的全部名单筛查
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param request 可选代操作 PayFac
     * @return 命令结果；筛查在提交后异步执行（processing=true），新执行成功后取代旧命中
     */
    @PostExchange("/cases/{caseId}/screenings/run")
    ClientReviewCommandResponseBody rerunScreenings(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewScreeningRunRequestBody request);

    /**
     * 处置一条筛查命中
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param hitId 命中 ID，必须属于该主案件或其子案件
     * @param request 处置结论、原因码与备注
     * @return 命令结果
     *         <li>命中已被新执行取代或已关闭 409 CLIENT_REVIEW_HIT_CLOSED。</li>
     */
    @PostExchange("/cases/{caseId}/screening-hits/{hitId}/resolution")
    ClientReviewCommandResponseBody resolveScreeningHit(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @PathVariable("hitId") String hitId, @Valid @RequestBody ClientReviewHitResolutionRequestBody request);

    /**
     * 手工客户风险评级（CRR）：评级、因子与说明一并替换当前值
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param request 评级、因子与说明
     * @return 命令结果；改评级会清除已有推荐（需重新推荐）
     */
    @PutExchange("/cases/{caseId}/risk-rating")
    ClientReviewCommandResponseBody rateRisk(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewRiskRatingRequestBody request);

    /**
     * 推荐人给出推荐结论
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param request 推荐结论与理由
     * @return 命令结果；可重复推荐，以最新一次为准
     */
    @PostExchange("/cases/{caseId}/recommendation")
    ClientReviewCommandResponseBody recommend(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewRecommendationRequestBody request);

    /**
     * O07 退回修改：主案件与仍在进行的子案件结束为 RETURNED，登记需求级错误与补件期限，结果送回 client-master
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param request 原因、需求级错误、期限与客户可见说明
     * @return 命令结果；已以其他结果结束 409 CLIENT_REVIEW_CLOSED
     */
    @PostExchange("/cases/{caseId}/return")
    ClientReviewCommandResponseBody returnCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewReturnRequestBody request);

    /**
     * O08 批准：门禁满足后主案件与全部子案件一起结束为 APPROVED，冻结档次、评级、辖区、责任方、推荐人与决定人
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID（决定人）
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param request 原因与可选客户可见说明
     * @return 命令结果；门禁未满足（含四眼）422 CLIENT_REVIEW_NOT_READY，detail 列出未满足项
     */
    @PostExchange("/cases/{caseId}/approval")
    ClientReviewCommandResponseBody approveCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewDecisionRequestBody request);

    /**
     * O09 最终拒绝：主案件 REJECTED，仍在进行的子案件随之撤回
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID（决定人）
     * @param sessionId Gateway 传入的会话标识
     * @param caseId 主案件 ID
     * @param request 原因与可选客户可见说明
     * @return 命令结果；四眼开启时须已有推荐且决定人与推荐人不同，否则 422 CLIENT_REVIEW_NOT_READY
     */
    @PostExchange("/cases/{caseId}/rejection")
    ClientReviewCommandResponseBody rejectCase(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClientReviewDecisionRequestBody request);
}
