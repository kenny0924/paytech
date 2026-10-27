package com.paytech.client.master.api.onboarding;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.client.master.api.onboarding.request.DeclarationRequestBody;
import com.paytech.client.master.api.onboarding.response.DeclarationResponseBody;
import com.paytech.client.master.api.onboarding.response.RequirementsResponseBody;
import com.paytech.client.master.api.onboarding.response.WithdrawalResponseBody;

import jakarta.validation.Valid;

/** CM-6 / CM-7 / CM-8：申请声明、需求清单与撤回；只允许 client-console-bff，每次核验 Platform 会话与本库归属。 */
@HttpExchange("/client-onboarding/v1/applications")
public interface ClientOnboardingApplicationApi {
    /**
     * 记录一项声明（只追加；同类型同取值同条款版本重复提交返回原记录）
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @param clientIp Gateway 传入的可信客户端 IP，可空
     * @param userAgent 浏览器 User-Agent，可空
     * @param applicationId 申请 ID
     * @param body 声明
     * @return 声明记录
     *         <li>只在 DRAFT/RETURNED 可写，否则 409 APPLICATION_NOT_EDITABLE；声明类型不在申请 Schema 中 422 INVALID_DRAFT。</li>
     */
    @PostExchange("/{applicationId}/declarations")
    DeclarationResponseBody declare(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestHeader(value = "X-Gateway-Client-Ip", required = false) String clientIp,
        @RequestHeader(value = "User-Agent", required = false) String userAgent,
        @PathVariable("applicationId") String applicationId, @Valid @RequestBody DeclarationRequestBody body);

    /**
     * 实时计算的需求清单（与正式提交校验同源，不落库）
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @param applicationId 申请 ID
     * @return 清单；审核中的申请材料为 PENDING_VERIFICATION，退回申请带补件期限
     *         <li>compliance 或 Platform 暂不可用 503，不视为没有问题。</li>
     *         <li>2026-10-27 之前的遗留申请没有采集 Schema：返回 ready=false 与空清单。</li>
     */
    @GetExchange("/{applicationId}/requirements")
    RequirementsResponseBody requirements(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("applicationId") String applicationId);

    /**
     * 撤回申请：DRAFT / RETURNED → WITHDRAWN，释放名额；已立案的审核案件由补偿任务通知 compliance 撤回
     *
     * @param caller 私网调用方，固定为 client-console-bff
     * @param accountId 当前 CLIENT 账号
     * @param sessionId 当前 CLIENT 会话
     * @param applicationId 申请 ID
     * @return 撤回结果；已撤回的申请重复调用返回原结果
     *         <li>SUBMITTED、UNDER_REVIEW 或其他终态：409 APPLICATION_NOT_WITHDRAWABLE。</li>
     */
    @PostExchange("/{applicationId}/withdrawal")
    WithdrawalResponseBody withdraw(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("applicationId") String applicationId);
}
