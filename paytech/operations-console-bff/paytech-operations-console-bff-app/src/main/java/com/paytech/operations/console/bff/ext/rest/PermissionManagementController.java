package com.paytech.operations.console.bff.ext.rest;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.PermissionManagementModels.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import com.paytech.operations.console.bff.integration.PlatformPermissionManagementClient;
/** 页面入口沿用 PLT-I02、Origin 和可信身份拦截器。 */
@RestController
@Validated
@lombok.AllArgsConstructor
@RequestMapping("/permission-admin/v1")
public class PermissionManagementController {
    private final PlatformPermissionManagementClient client;

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/permissions")
    public PagingResponseBody<PermissionSummary> permissions(
        OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "riskLevel", required = false) String riskLevel) {
        return client.permissions(context, page, size, code, resourceType, status, riskLevel);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/permissions/{permissionCode}")
    public PermissionResponseBody permission(
        OperatorContext context,
        @PathVariable("permissionCode") String permissionCode) {
        return client.permission(context, permissionCode);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PostMapping("/permissions")
    public ConfigurationMutationResponseBody createPermission(
        OperatorContext context,
        @Valid @RequestBody CreatePermissionRequestBody request) {
        return client.createPermission(context, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PutMapping("/permissions/{permissionCode}")
    public ConfigurationMutationResponseBody updatePermission(
        OperatorContext context,
        @PathVariable("permissionCode") String permissionCode,
        @Valid @RequestBody UpdatePermissionRequestBody request) {
        return client.updatePermission(context, permissionCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @PatchMapping("/permissions/{permissionCode}/status")
    public ConfigurationMutationResponseBody changePermissionStatus(
        OperatorContext context,
        @PathVariable("permissionCode") String permissionCode,
        @Valid @RequestBody ChangePermissionStatusRequestBody request) {
        return client.changePermissionStatus(context, permissionCode, request);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/permissions/{permissionCode}/references")
    public PagingResponseBody<PermissionReference> references(
        OperatorContext context,
        @PathVariable("permissionCode") String permissionCode,
        @RequestParam(value = "kind", required = true) String kind,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.references(context, permissionCode, kind, page, size);
    }

    /** 简单转发，不把 HUMAN_AUTHENTICATED 误当成最终授权。 */
    @GetMapping("/permission-options")
    public PagingResponseBody<PermissionOption> options(
        OperatorContext context,
        @RequestParam(value = "usage", required = true) String usage,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.options(context, usage, page, size, keyword);
    }
}
