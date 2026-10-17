package com.paytech.operations.console.bff.ext.rest;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.paytech.operations.console.bff.integration.PlatformApiCredentialManagementClient;

/** 沿用公共 Origin、PLT-I02 和可信身份边界。 */
@RestController
@org.springframework.validation.annotation.Validated
@lombok.AllArgsConstructor
@RequestMapping("/api-credential-admin/v1")
public class ApiCredentialManagementController {
    private final PlatformApiCredentialManagementClient client;

    /** 当前数据查询。 */
    @GetMapping("/credentials")
    public PagingResponseBody<ApiCredentialSummary> credentials(OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "clientId", required = false) String clientId,
        @RequestParam(value = "clientName", required = false) String clientName,
        @RequestParam(value = "credentialId", required = false) String credentialId,
        @RequestParam(value = "keyPrefix", required = false) String keyPrefix,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "scopeCode", required = false) String scopeCode) {
        return client.credentials(context, page, size, clientId, clientName, credentialId, keyPrefix, status, scopeCode);
    }

    /** 当前数据查询。 */
    @GetMapping("/credentials/{credentialId}")
    public ApiCredentialDetailResponseBody credential(OperatorContext context,
        @PathVariable("credentialId") String credentialId) {
        return client.credential(context, credentialId);
    }

    /** 当前数据查询。 */
    @GetMapping("/credentials/{credentialId}/scopes")
    public PagingResponseBody<ApiCredentialScopeGrant> scopes(OperatorContext context,
        @PathVariable("credentialId") String credentialId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.scopes(context, credentialId, page, size);
    }

    /** 保留版本与原因的管理写入。 */
    @PostMapping("/credentials/{credentialId}/revoke")
    public ApiCredentialRevokeResponseBody revoke(OperatorContext context,
        @PathVariable("credentialId") String credentialId,
        @Valid @RequestBody RevokeApiCredentialRequestBody request) {
        return client.revoke(context, credentialId, request);
    }
}
