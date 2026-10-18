package com.paytech.client.contract.api.onboarding;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.client.contract.api.onboarding.request.ClassificationSetRequestBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationOptionsResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;

import jakarta.validation.Valid;

/**
 * O03/O04：Operations 为审核中的 Client 申请设置并确认行业与 MCC；由 Operations BFF 调用
 * <li>操作者来自 Gateway 可信头；路由权限由 Gateway/BFF 绑定，CCT 只记录确认人。</li>
 * <li>按申请当前状态防重，不读取 Idempotency-Key。</li>
 */
@HttpExchange("/client-onboarding-admin/v1")
public interface ClientClassificationAdminApi {

    /**
     * 可选行业目录
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @return CCT 维护的通用行业列表；MCC 只校验四位数字
     */
    @GetExchange("/classification-options")
    ClassificationOptionsResponseBody options(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 读取审核任务当前分类
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId Risk 主审核任务 ID
     * @return 本任务的分类集合及是否已确认；尚未设置时为空集合
     *         <li>任务不是任何申请的当前任务返回 404 REVIEW_CASE_NOT_FOUND。</li>
     */
    @GetExchange("/cases/{caseId}/classifications")
    ClassificationSetResponseBody get(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId);

    /**
     * O03 设置分类：整体替换本任务的分类集合
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId Risk 主审核任务 ID
     * @param request 1—20 项，组合不重复且恰好一个主要分类
     * @return 保存后的未确认集合
     *         <li>申请不在审核中返回 409 APPLICATION_NOT_EDITABLE；本任务已确认返回 409 CLASSIFICATION_CONFIRMED。</li>
     *         <li>行业不在目录、组合重复或主要分类不是恰好一个返回 422 CLASSIFICATION_INVALID。</li>
     */
    @PutExchange("/cases/{caseId}/classifications")
    ClassificationSetResponseBody replace(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId,
        @Valid @RequestBody ClassificationSetRequestBody request);

    /**
     * O04 确认分类：固定本任务分类，提交后交给 Risk（I02）
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param caseId Risk 主审核任务 ID
     * @return 已确认集合；已确认时直接返回原确认结果
     *         <li>本任务尚未设置分类返回 422 CLASSIFICATION_INVALID。</li>
     *         <li>Body 为空；交给 Risk 在本地提交后异步完成，失败由补偿任务重试。</li>
     */
    @PostExchange("/cases/{caseId}/classification-confirmation")
    ClassificationSetResponseBody confirm(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("caseId") String caseId);
}
