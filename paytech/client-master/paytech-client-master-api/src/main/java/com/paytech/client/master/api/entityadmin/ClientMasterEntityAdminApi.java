package com.paytech.client.master.api.entityadmin;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.client.master.api.entityadmin.request.ChangeAcquiringQualificationRequestBody;
import com.paytech.client.master.api.entityadmin.request.ReplacePartnerRolesRequestBody;
import com.paytech.client.master.api.entityadmin.response.ClientSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.LocalPartnerSummaryResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntityDetailResponseBody;
import com.paytech.client.master.api.entityadmin.response.OperatingEntitySummaryResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * 运营维护经营主体（OE）与 Local Partner 主档属性；由 Operations BFF 调用
 * <li>操作者来自 Gateway 可信头；路由权限由 Gateway/BFF 绑定，client-master 只校验会话存在并记录操作者。</li>
 * <li>成功响应无外壳；错误为 Problem。写接口按根修订号防止覆盖，不读取 Idempotency-Key。</li>
 * <li>不提供 OE / Partner 创建；准入范围与卖方披露只读。</li>
 */
@HttpExchange("/client-master-admin/v1")
public interface ClientMasterEntityAdminApi {

    /**
     * 经营主体分页列表
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param status 可选，OE 状态 Code
     * @param sourceType 可选，当前有效来源类型 CLIENT 或 PARTNER
     * @param country 可选，经营国家 ISO 3166-1 alpha-2
     * @param page 可选页码，默认 1
     * @param size 可选每页条数 10、20、50 或 100，默认 10
     * @return 按 entityNo 升序的分页结果
     *         <li>筛选值或分页参数非法返回 400 INVALID_REQUEST。</li>
     */
    @GetExchange("/operating-entities")
    PagingResponseBody<OperatingEntitySummaryResponseBody> listOperatingEntities(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "country", required = false) String country,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 经营主体详情
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param entityNo OE 编号
     * @return 主档、当前来源、提供方、准入范围、卖方披露与根修订号
     *         <li>不存在或编号格式非法返回 404 OPERATING_ENTITY_NOT_FOUND。</li>
     */
    @GetExchange("/operating-entities/{entityNo}")
    OperatingEntityDetailResponseBody getOperatingEntity(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("entityNo") String entityNo);

    /**
     * 修改经营主体收单资质
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param entityNo OE 编号
     * @param request 目标资质、读取时的根修订号与原因
     * @return 修改后的详情，changeVersion 加 1
     *         <li>不存在返回 404 OPERATING_ENTITY_NOT_FOUND；资质取值非法返回 422 ENTITY_ATTRIBUTE_INVALID。</li>
     *         <li>根修订号不一致返回 409 ENTITY_CHANGE_VERSION_CONFLICT；同一 OE 正在修改返回 409 CONCURRENT_REQUEST。</li>
     */
    @PutExchange("/operating-entities/{entityNo}/acquiring-qualification")
    OperatingEntityDetailResponseBody changeAcquiringQualification(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("entityNo") String entityNo,
        @Valid @RequestBody ChangeAcquiringQualificationRequestBody request);

    /**
     * Local Partner 分页列表
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param status 可选，Local Partner 状态 Code
     * @param role 可选，当前 ACTIVE 角色 OE_PROVIDER 或 REFERRER
     * @param page 可选页码，默认 1
     * @param size 可选每页条数 10、20、50 或 100，默认 10
     * @return 按 localPartnerId 升序的分页结果
     *         <li>筛选值或分页参数非法返回 400 INVALID_REQUEST。</li>
     */
    @GetExchange("/local-partners")
    PagingResponseBody<LocalPartnerSummaryResponseBody> listLocalPartners(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "role", required = false) String role,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * Local Partner 详情
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param localPartnerId Local Partner ID，正十进制 BIGINT
     * @return 主档、角色、提供的经营主体与根修订号
     *         <li>不存在或 ID 格式非法返回 404 LOCAL_PARTNER_NOT_FOUND。</li>
     */
    @GetExchange("/local-partners/{localPartnerId}")
    LocalPartnerDetailResponseBody getLocalPartner(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("localPartnerId") String localPartnerId);

    /**
     * 整体替换 Local Partner 角色
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param localPartnerId Local Partner ID
     * @param request 替换后的全部角色、读取时的根修订号与原因
     * @return 修改后的详情，changeVersion 加 1
     *         <li>不存在返回 404 LOCAL_PARTNER_NOT_FOUND；角色为空、重复、取值非法或仍在提供经营主体却去掉 OE_PROVIDER 返回
     *         422 ENTITY_ATTRIBUTE_INVALID。</li>
     *         <li>根修订号不一致返回 409 ENTITY_CHANGE_VERSION_CONFLICT；同一 Partner 正在修改返回 409 CONCURRENT_REQUEST。</li>
     */
    @PutExchange("/local-partners/{localPartnerId}/roles")
    LocalPartnerDetailResponseBody replacePartnerRoles(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("localPartnerId") String localPartnerId,
        @Valid @RequestBody ReplacePartnerRolesRequestBody request);

    /**
     * 资格登记前核对 Client 主体
     *
     * @param actorAccountId Gateway 传入的操作者 Account ID
     * @param sessionId Gateway 传入的会话标识
     * @param clientId Client ID，正十进制 BIGINT
     * @return Client 编号、Party、类型、状态与法定名称
     *         <li>不存在或 ID 格式非法返回 404 CLIENT_NOT_FOUND。</li>
     */
    @GetExchange("/clients/{clientId}/summary")
    ClientSummaryResponseBody getClientSummary(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("clientId") String clientId);
}
