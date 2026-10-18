package com.paytech.platform.support.ext.rest.apicredentialmanagement;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.ApiCredentialSummaryView;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.CredentialFilter;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListApiCredentialsRequest;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialSummaryResponseBody;

/** 凭证列表的 Query 解析与 HTTP 响应转换，不承担鉴权或数据库访问。 */
@Component
public class ApiCredentialListConverter {

    /** 在协议边界保留原有 ID、分页、枚举及 LIKE 字面量规则，GET 参数位置不变。 */
    public ListApiCredentialsRequest toRequest(String actor, String session, Integer page, Integer size,
        String clientId, String clientName, String credentialId, String keyPrefix, String status, String scopeCode) {
        var paging = ClientApiManagementInputs.paging(page, size);
        var filter = new CredentialFilter(clientId == null ? null : ClientApiManagementInputs.id(clientId),
            ClientApiManagementInputs.contains(clientName, 255, "clientName"),
            credentialId == null ? null : ClientApiManagementInputs.id(credentialId),
            keyPrefix == null ? null : ClientApiManagementInputs.prefix(keyPrefix),
            status == null ? null : ConfigurationInputs.enumeration(ApiCredentialStatus.class, status),
            scopeCode == null ? null : ClientApiManagementInputs.scopeCode(scopeCode));
        return new ListApiCredentialsRequest(actor, session, paging, filter);
    }

    /** 仅投影合同批准的字段，不暴露 Tenant 类型或内部查询对象。 */
    public ApiCredentialSummaryResponseBody toResponseBody(ApiCredentialSummaryView value) {
        return new ApiCredentialSummaryResponseBody(id(value.getCredentialId()), id(value.getTenantId()),
            id(value.getClientId()), value.getClientName(), value.getKeyPrefix(), value.getStoredStatus().getCode(),
            value.getStatus().getCode(), value.getTenantOwnerStatus().getCode(), value.getTenantSecurityStatus().getCode(),
            value.getExpiresTime(), value.getVersion(), value.getCreateTime(), value.getUpdateTime());
    }

    /** 保留可空身份语义，使用十进制字符串避免浏览器整数精度丢失。 */
    private String id(Long value) {
        return value == null ? null : value.toString();
    }
}
