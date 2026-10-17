package com.paytech.platform.support.ext.rest.apicredentialmanagement;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialDetailView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialScopeGrantView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Mutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.service.processor.request.GetApiCredentialRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListApiCredentialScopesRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.RevokeApiCredentialRequest;
import com.paytech.platform.support.api.apicredentialmanagement.request.RevokeApiCredentialRequestBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialDetailResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialRevokeResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialScopeGrantResponseBody;

/** 凭证详情、授权列表与撤销的协议转换，不参与事务或鉴权。 */
@Component
public class ApiCredentialManagementConverter {

    /** 保留原 Path ID 解析规则，构造内部详情请求。 */
    public GetApiCredentialRequest toDetailRequest(String actor, String session, String credentialId) {
        return new GetApiCredentialRequest(actor, session, ClientApiManagementInputs.id(credentialId));
    }

    /** 校验并补齐分页参数，保留每页最多 100 条及正整数身份合同。 */
    public ListApiCredentialScopesRequest toScopesRequest(String actor, String session, String credentialId,
        Integer page, Integer size) {
        var paging = ClientApiManagementInputs.paging(page, size);
        return new ListApiCredentialScopesRequest(actor, session, ClientApiManagementInputs.id(credentialId), paging);
    }

    /** Body 由入口 Bean Validation 校验；本层规范化原因并解析 Path。 */
    public RevokeApiCredentialRequest toRevokeRequest(String actor, String session, String credentialId,
        RevokeApiCredentialRequestBody body) {
        return new RevokeApiCredentialRequest(actor, session, ClientApiManagementInputs.id(credentialId), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 只投影批准的详情字段；稳定身份按十进制字符串输出。 */
    public ApiCredentialDetailResponseBody toResponseBody(ApiCredentialDetailView value) {
        return new ApiCredentialDetailResponseBody(id(value.getCredentialId()), id(value.getTenantId()),
            id(value.getClientId()), value.getClientName(), value.getKeyPrefix(), value.getStoredStatus().getCode(),
            value.getStatus().getCode(), value.getTenantOwnerStatus().getCode(),
            value.getTenantSecurityStatus().getCode(), value.getExpiresTime(), value.getVersion(),
            value.getCreateTime(), value.getUpdateTime(), id(value.getRotatedFromId()));
    }

    /** 授权明细保留独立派生状态与可空目录名称，不投影父凭证状态。 */
    public ApiCredentialScopeGrantResponseBody toResponseBody(ApiCredentialScopeGrantView value) {
        return new ApiCredentialScopeGrantResponseBody(id(value.getScopeId()), value.getScopeCode(),
            value.getDisplayName(), value.getResourcePattern(), value.getActionCode(),
            value.getStoredStatus().getCode(), value.getStatus().getCode(), value.getEffectiveFrom(),
            value.getEffectiveTo(), value.getVersion(), value.getCreateTime(), value.getUpdateTime());
    }

    /** 使用本次凭证状态及版本构造响应。 */
    public ApiCredentialRevokeResponseBody toResponseBody(Mutation value) {
        return new ApiCredentialRevokeResponseBody(Long.toString(value.objectId()),
            ApiCredentialStatus.REVOKED.getCode(), value.version());
    }

    /** 保留可空身份与浏览器整数精度。 */
    private String id(Long value) {
        return value == null ? null : value.toString();
    }
}
