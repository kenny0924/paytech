package com.paytech.platform.support.ext.rest.tenantprojection;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionResult;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetTenantProjectionRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.ProjectTenantStatusRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.ProvisionTenantRequest;
import com.paytech.platform.support.api.tenantprojection.request.ProvisionRequestBody;
import com.paytech.platform.support.api.tenantprojection.request.StatusRequestBody;
import com.paytech.platform.support.api.tenantprojection.response.ProjectionResponseBody;

/** PLT-I17 协议转换；不持有来源判断、资源锁或本地事务。 */
@Component
public class TenantProjectionConverter {
    /** HTTP @Valid 已校验必填、类型、范围与摘要格式。 */
    public ProvisionTenantRequest provision(ProvisionRequestBody body) {
        return new ProvisionTenantRequest(id(body.getOwnerRefId()), id(body.getRequestedByAccountId()),
            body.getSourceVersion(), body.getPayloadHash());
    }

    /** 状态代码在进入锁之前转换，不能通过状态投影替换申请账号。 */
    public ProjectTenantStatusRequest status(StatusRequestBody body) {
        return new ProjectTenantStatusRequest(id(body.getOwnerRefId()), id(body.getRequestedByAccountId()),
            body.getSourceVersion(), body.getPayloadHash(),
            ConfigurationInputs.enumeration(OperationsTenantOwnerStatus.class, body.getOwnerStatus()));
    }

    /** 查询类型由公共 Processor 约束校验，路径 ID 保持规范十进制。 */
    public GetTenantProjectionRequest query(String tenantType, String ownerRefId) {
        return new GetTenantProjectionRequest(tenantType, id(ownerRefId));
    }

    /** ID 始终以字符串跨越 HTTP，不增加资料版本或令牌能力。 */
    public ProjectionResponseBody response(TenantProjectionResult result) {
        return new ProjectionResponseBody("CLIENT", Long.toString(result.getOwnerRefId()),
            Long.toString(result.getRequestedByAccountId()), Long.toString(result.getTenantId()),
            Long.toString(result.getMemberId()), result.getOwnerStatus().getCode(), result.getSourceVersion(),
            result.getPayloadHash());
    }

    /** 把协议 ID 转成内部 long，溢出仍属于参数错误。 */
    private long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}"))
            throw new InvalidParameterException("来源标识格式错误");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new InvalidParameterException("来源标识超出范围");
        }
    }
}
