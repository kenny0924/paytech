package com.paytech.operations.console.bff.ext.rest;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskListClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklist.AddRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.CreateRiskListRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.DeactivateRiskListEntryRequestBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListEntryResponseBody;
import com.paytech.operations.console.bff.service.model.risklist.RiskListResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 风控名单维护的浏览器入口。
 *
 * <p>每个路由由 Gateway 绑定权限并经 PLT-I02 实时授权；简单转发直接委托 RiskListClient。主体原值只出现在成员查询参数和加入请求中，
 * 整个前缀只记元数据日志，响应只返回 Risk 的脱敏主体键。
 */
@RestController
@AllArgsConstructor
@RequestMapping("/risk-lists/v1/lists")
public class RiskListController {
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String ID = "[1-9][0-9]{0,18}";

    private final RiskListClient client;

    /** 名单分页，按编码升序。 */
    @GetMapping
    public PagingResponseBody<RiskListResponseBody> lists(HttpServletRequest servletRequest, OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listLists(context, paging.page(), paging.size());
    }

    /** 新建名单。 */
    @PostMapping
    public RiskListResponseBody createList(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid CreateRiskListRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createList(context, body);
    }

    /** 成员分页；subjectValue 为主体原值的精确匹配。 */
    @GetMapping("/{listCode}/entries")
    public PagingResponseBody<RiskListEntryResponseBody> entries(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("listCode") @Pattern(regexp = CODE) String listCode,
        @RequestParam(value = "subjectValue", required = false) @Size(min = 1, max = 256) String subjectValue,
        @RequestParam(value = "status", required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("subjectValue", "status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listEntries(context, listCode, subjectValue, status, paging.page(), paging.size());
    }

    /** 加入成员。 */
    @PostMapping("/{listCode}/entries")
    public RiskListEntryResponseBody addEntry(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("listCode") @Pattern(regexp = CODE) String listCode,
        @RequestBody @NotNull @Valid AddRiskListEntryRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.addEntry(context, listCode, body);
    }

    /** 失效成员。 */
    @PostMapping("/{listCode}/entries/{entryId}/deactivate")
    public RiskListEntryResponseBody deactivateEntry(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("listCode") @Pattern(regexp = CODE) String listCode,
        @PathVariable("entryId") @Pattern(regexp = ID) String entryId,
        @RequestBody @NotNull @Valid DeactivateRiskListEntryRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.deactivateEntry(context, listCode, entryId, body);
    }

    /** 拒绝未声明、重复或为空的 Query 参数。 */
    static void requireQueryKeys(HttpServletRequest request, Set<String> allowedKeys) {
        for (var entry : request.getParameterMap().entrySet()) {
            if (!allowedKeys.contains(entry.getKey()) || entry.getValue().length != 1 || entry.getValue()[0].isEmpty()) {
                throw new InvalidParameterException("请求包含不支持、重复或为空的 Query 参数");
            }
        }
    }
}
