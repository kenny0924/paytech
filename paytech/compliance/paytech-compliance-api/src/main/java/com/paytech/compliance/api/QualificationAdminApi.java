package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.compliance.api.request.QualificationManualDecisionRequestBody;
import com.paytech.compliance.api.request.QualificationRevocationRequestBody;
import com.paytech.compliance.api.response.QualificationHistoryResponseBody;
import com.paytech.compliance.api.response.QualificationResponseBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import jakarta.validation.Valid;

/**
 * Operations 资格登记页的私网合同；由 Operations BFF 调用
 * <li>操作者来自 Gateway 可信头；菜单与路由权限由 Gateway/BFF 绑定，compliance 只记录操作人。</li>
 * <li>compliance 不出站核对主体是否存在，登记前由 Operations BFF 先查 client-master。</li>
 * <li>写命令以资格版本条件保证并发：页面版本不一致或保存时已变化 409 QUALIFICATION_CHANGED；不读取 Idempotency-Key。</li>
 */
@HttpExchange("/compliance-admin/v1")
public interface QualificationAdminApi {

    /**
     * 角色资格分页，按更新时间倒序
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param roleType
     *            可选角色类型：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER
     * @param roleId
     *            可选角色标识；必须与 roleType 一起提供，格式不符 422 QUALIFICATION_INVALID
     * @param qualificationType
     *            可选资格类型
     * @param status
     *            可选存储状态：APPROVED、REJECTED、REVOKED、EXPIRED
     * @param page
     *            页码，缺省 1
     * @param size
     *            每页条数，缺省 10，只允许 10、20、50、100、200
     * @return 资格分页；每项含查询时实时判定的 effectiveStatus 与当前决定来源
     */
    @GetExchange("/qualifications")
    PagingResponseBody<QualificationResponseBody> listQualifications(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "roleType", required = false) String roleType,
        @RequestParam(value = "roleId", required = false) String roleId,
        @RequestParam(value = "qualificationType", required = false) String qualificationType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 一个资格的状态变更与对应决定，按资格版本倒序
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param qualificationId
     *            角色资格 ID
     * @return 状态变更历史；资格不存在 404 QUALIFICATION_NOT_FOUND
     */
    @GetExchange("/qualifications/{qualificationId}/history")
    QualificationHistoryResponseBody getQualificationHistory(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("qualificationId") String qualificationId);

    /**
     * 手工登记一条已批准的角色资格；没有资格时新建，已有资格时切换为本次登记
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param request
     *            角色、资格类型、有效期、原因与依据
     * @return 登记后的资格
     *         <li>组合、角色标识或有效期不合法 422 QUALIFICATION_INVALID；页面版本不符 409 QUALIFICATION_CHANGED。</li>
     */
    @PostExchange("/qualifications/manual-decisions")
    QualificationResponseBody recordManualDecision(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody QualificationManualDecisionRequestBody request);

    /**
     * 撤销一条当前已批准的角色资格
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param qualificationId
     *            角色资格 ID
     * @param request
     *            页面版本、原因与依据
     * @return 撤销后的资格
     *         <li>资格不存在 404 QUALIFICATION_NOT_FOUND；当前不是 APPROVED 409 QUALIFICATION_NOT_ACTIVE；版本不符 409
     *         QUALIFICATION_CHANGED。</li>
     */
    @PostExchange("/qualifications/{qualificationId}/revocation")
    QualificationResponseBody revokeQualification(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("qualificationId") String qualificationId,
        @Valid @RequestBody QualificationRevocationRequestBody request);
}
