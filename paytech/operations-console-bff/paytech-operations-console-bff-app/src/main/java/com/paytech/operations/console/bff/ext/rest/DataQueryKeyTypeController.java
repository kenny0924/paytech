package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformDataQueryClient;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryKeyTypeStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryKeyTypeRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ReorderQueryKeyTypesRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryKeyTypeRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryKeyTypeResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.ReorderQueryKeyTypesResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 联合查询键类型页的浏览器入口，全部同路径简单转发 Platform
 * <li>路由由 Gateway 绑定 DATA_QUERY_READ（列表）或 DATA_QUERY_TEMPLATE_MANAGE（新建、修改、启停、排序），并经 PLT-I02 实时授权；
 * Platform 再次校验权限。</li>
 * <li>Query 与 Body 按数据查询的严格边界解析：未知字段、重复或多余 Query、查询带 Body 均在访问 Platform 前拒绝。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/data-query-admin/v1")
public class DataQueryKeyTypeController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final PlatformDataQueryClient client;

    /** 键类型分页，按 sortNo、keyTypeCode 升序。 */
    @GetMapping("/key-types")
    public PagingResponseBody<QueryKeyTypeResponseBody> listQueryKeyTypes(OperatorContext context,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @RequestParam(value = "keyword", required = false) @Size(max = 64) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listQueryKeyTypes(context, status, keyword, page, size);
    }

    /** 新建 ACTIVE 键类型。 */
    @PostMapping("/key-types")
    public ConfigurationMutationResponseBody createQueryKeyType(OperatorContext context,
        @Valid @RequestBody CreateQueryKeyTypeRequestBody request) {
        return client.createQueryKeyType(context, request);
    }

    /** 按原版本修改键类型；被引用时不能修改 valueType。 */
    @PutMapping("/key-types/{keyTypeId}")
    public ConfigurationMutationResponseBody updateQueryKeyType(OperatorContext context,
        @PathVariable("keyTypeId") @Pattern(regexp = ID) String keyTypeId,
        @Valid @RequestBody UpdateQueryKeyTypeRequestBody request) {
        return client.updateQueryKeyType(context, keyTypeId, request);
    }

    /** 启用或停用键类型；被引用时不能停用。 */
    @PostMapping("/key-types/{keyTypeId}/status")
    public ConfigurationMutationResponseBody changeQueryKeyTypeStatus(OperatorContext context,
        @PathVariable("keyTypeId") @Pattern(regexp = ID) String keyTypeId,
        @Valid @RequestBody ChangeQueryKeyTypeStatusRequestBody request) {
        return client.changeQueryKeyTypeStatus(context, keyTypeId, request);
    }

    /** 拖动排序后保存全部键类型的新顺序。 */
    @PutMapping("/key-type-order")
    public ReorderQueryKeyTypesResponseBody reorderQueryKeyTypes(OperatorContext context,
        @Valid @RequestBody ReorderQueryKeyTypesRequestBody request) {
        return client.reorderQueryKeyTypes(context, request);
    }
}
