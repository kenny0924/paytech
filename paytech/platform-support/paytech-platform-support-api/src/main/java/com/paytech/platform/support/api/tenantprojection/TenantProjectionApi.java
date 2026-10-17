package com.paytech.platform.support.api.tenantprojection;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;

import jakarta.validation.Valid;

/** PLT-I17：仅 CCT 私网 Owner 调用，不登记 Gateway 公共路由。 */
@HttpExchange("/tenant-projections/v1/tenants")
public interface TenantProjectionApi {
    /**
     * 来源第一版原子创建 PENDING Tenant、OWNER Member 与入驻角色。
     * 
     * @param request
     *            原始 Client 来源身份、冻结申请账号、第一版序号与摘要。
     * @return 当前已提交的稳定 Tenant/Member 身份及来源位置；相同来源重放不重复写入。
     */
    @PostExchange("/provision")
    ProjectionResponseBody provision(@Valid @RequestBody ProvisionRequestBody request);

    /**
     * 顺序投影 CCT 生命周期；先于开通或跳版本返回可重试的未就绪错误。
     * 
     * @param request
     *            同一 Client 与申请账号的来源序号、摘要及目标业务状态。
     * @return 当前已提交位置；旧状态事实不回退投影，首次 ACTIVE 同事务切换 OWNER 角色。
     */
    @PostExchange("/status")
    ProjectionResponseBody projectStatus(@Valid @RequestBody StatusRequestBody request);

    /**
     * 恢复未知结果时按来源查询，不能凭此建立浏览器授权。
     * 
     * @param tenantType
     *            来源类型，本期只接受 CLIENT。
     * @param ownerRefId
     *            CCT Client 的正十进制标识。
     * @return 当前已提交位置和稳定工作区身份；来源不存在返回 404。
     */
    @GetExchange("/{tenantType}/{ownerRefId}")
    ProjectionResponseBody projection(@PathVariable("tenantType") String tenantType,
        @PathVariable("ownerRefId") String ownerRefId);
}
