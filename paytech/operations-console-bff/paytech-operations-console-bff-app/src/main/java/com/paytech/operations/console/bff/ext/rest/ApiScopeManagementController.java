package com.paytech.operations.console.bff.ext.rest;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.*;
import com.paytech.operations.console.bff.service.model.ApiScopePageModels.ApiScopePageResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.paytech.operations.console.bff.integration.PlatformApiScopeManagementClient;
import com.paytech.operations.console.bff.service.processor.ApiScopeDetailsProcessor;
import com.paytech.operations.console.bff.service.processor.request.ApiScopeDetailsRequest;

/** 沿用公共 Origin、PLT-I02 和可信身份边界。 */
@RestController
@org.springframework.validation.annotation.Validated
@lombok.AllArgsConstructor
@RequestMapping("/api-scope-admin/v1")
public class ApiScopeManagementController {
    private final ApiScopeDetailsProcessor details;
    private final PlatformApiScopeManagementClient client;

    /** 当前数据查询。 */
    @GetMapping("/scopes")
    public PagingResponseBody<ApiScopeSummary> scopes(OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.scopes(context, page, size, keyword);
    }

    /** Scope 详情页，按 referenceKind 附带一种引用分页。 */
    @GetMapping("/scopes/{scopeCode}")
    public ApiScopePageResponseBody scope(OperatorContext context,
        @PathVariable("scopeCode") String scopeCode,
        @RequestParam(value = "referenceKind", required = false) String referenceKind,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return details.process(new ApiScopeDetailsRequest(context, scopeCode, referenceKind, page, size));
    }

    /** 保留版本与原因的管理写入。 */
    @PostMapping("/scopes")
    public ApiScopeMutationResponseBody create(OperatorContext context,
        @Valid @RequestBody CreateApiScopeRequestBody request) {
        return client.create(context, request);
    }

    /** 保留版本与原因的管理写入。 */
    @PutMapping("/scopes/{scopeCode}")
    public ApiScopeMutationResponseBody update(OperatorContext context,
        @PathVariable("scopeCode") String scopeCode,
        @Valid @RequestBody UpdateApiScopeRequestBody request) {
        return client.update(context, scopeCode, request);
    }

    /** 当前数据查询。 */
    @GetMapping("/scope-options")
    public PagingResponseBody<ApiScopeOption> options(OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.options(context, page, size, keyword);
    }
}
