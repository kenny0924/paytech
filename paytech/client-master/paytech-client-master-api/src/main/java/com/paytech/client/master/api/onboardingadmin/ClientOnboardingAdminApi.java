package com.paytech.client.master.api.onboardingadmin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.client.master.api.onboardingadmin.request.IssueOnboardingCodeRequestBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminCatalogItemResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientDetailResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminClientSummaryResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminJurisdictionResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminOnboardingSchemaResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.OnboardingCodeResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * CM-10 / CM-11 / CM-12：Operations BFF 使用的入驻配置只读查看、Client 列表与详情、PayFac 入驻码签发与作废
 * <li>操作者来自 Gateway 可信头，只校验存在并记录；证件只返回脱敏值，联系方式不 Reveal。</li>
 */
@HttpExchange("/client-master-admin/v1")
public interface ClientOnboardingAdminApi {
    /**
     * 全部辖区包及其规则开关与目录（只读）
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @return 辖区包，按代码排序
     */
    @GetExchange("/jurisdictions")
    List<AdminJurisdictionResponseBody> jurisdictions(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 辖区全部采集 Schema 版本及字段（只读）
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param jurisdiction 辖区代码
     * @return Schema 版本
     */
    @GetExchange("/onboarding-schemas")
    List<AdminOnboardingSchemaResponseBody> onboardingSchemas(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @RequestParam("jurisdiction") String jurisdiction);

    /**
     * 某通用目录的全部项（只读）
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param catalogCode 目录代码，如 INDUSTRY、REGISTRATION_AUTHORITY
     * @return 目录项
     */
    @GetExchange("/catalog-items")
    List<AdminCatalogItemResponseBody> catalogItems(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @RequestParam("catalogCode") String catalogCode);

    /**
     * Client 分页列表
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param status Client 状态，可空
     * @param jurisdiction 辖区，可空
     * @param responsiblePartnerId 责任 PayFac，可空
     * @param page 页码，默认 1
     * @param size 每页条数 10/20/50/100/200，默认 10
     * @return 分页结果，按 Client ID 倒序
     */
    @GetExchange("/clients")
    PagingResponseBody<AdminClientSummaryResponseBody> clients(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "jurisdiction", required = false) String jurisdiction,
        @RequestParam(value = "responsiblePartnerId", required = false) String responsiblePartnerId,
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size);

    /**
     * Client 详情
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param clientId Client ID
     * @return 主档、当前资料、人员、关系与申请历史；不存在 404 CLIENT_NOT_FOUND
     */
    @GetExchange("/clients/{clientId}")
    AdminClientDetailResponseBody client(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("clientId") String clientId);

    /**
     * Local Partner 的入驻码，新签发在前
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param localPartnerId Local Partner ID
     * @return 入驻码；Partner 不存在 404 LOCAL_PARTNER_NOT_FOUND
     */
    @GetExchange("/local-partners/{localPartnerId}/onboarding-codes")
    List<OnboardingCodeResponseBody> onboardingCodes(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("localPartnerId") String localPartnerId);

    /**
     * 签发入驻码（随机 XXXX-XXXX）；只有 ACTIVE 且具备 ACTIVE OE_PROVIDER 角色的 Partner 可签发
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param localPartnerId Local Partner ID
     * @param body 有效期、次数与说明
     * @return 201 与新入驻码；Partner 不具备角色 422 PARTNER_NOT_OE_PROVIDER
     */
    @PostExchange("/local-partners/{localPartnerId}/onboarding-codes")
    ResponseEntity<OnboardingCodeResponseBody> issueOnboardingCode(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("localPartnerId") String localPartnerId,
        @Valid @RequestBody IssueOnboardingCodeRequestBody body);

    /**
     * 作废入驻码；已作废返回当前结果，已过期返回当前结果不改变
     *
     * @param actorAccountId Operations 账号
     * @param sessionId Operations 会话
     * @param localPartnerId Local Partner ID
     * @param codeId 入驻码 ID
     * @return 入驻码；不存在或不属于该 Partner 404 ONBOARDING_CODE_NOT_FOUND
     */
    @PostExchange("/local-partners/{localPartnerId}/onboarding-codes/{codeId}/revocation")
    OnboardingCodeResponseBody revokeOnboardingCode(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("localPartnerId") String localPartnerId,
        @PathVariable("codeId") String codeId);
}
