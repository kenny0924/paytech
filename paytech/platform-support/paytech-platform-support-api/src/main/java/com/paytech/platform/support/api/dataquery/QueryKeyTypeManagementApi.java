package com.paytech.platform.support.api.dataquery;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.request.ChangeQueryKeyTypeStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.request.ReorderQueryKeyTypesRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryKeyTypeRequestBody;
import com.paytech.platform.support.api.dataquery.response.QueryKeyTypeResponseBody;
import com.paytech.platform.support.api.dataquery.response.ReorderQueryKeyTypesResponseBody;

import jakarta.validation.Valid;

/**
 * 联合查询键类型维护；只供 operations-console-bff 私网调用，操作人由 Gateway 可信 Header 传递。
 * <li>查询需要 DATA_QUERY_READ；写操作需要 DATA_QUERY_TEMPLATE_MANAGE；无权限返回 403 OPERATIONS_PERMISSION_DENIED。</li>
 * <li>写操作登记审计；写接口带 expectedVersion，版本不符返回 409；键类型不存在返回 404。</li>
 * <li>被 ACTIVE 模板当前版本引用的键类型不能停用、不能修改 valueType，返回 400，消息列出前 5 个模板编码。</li>
 */
@HttpExchange(url = "/data-query-admin/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface QueryKeyTypeManagementApi {

    /**
     * 分页查询键类型
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param status 可选：ACTIVE、INACTIVE
     * @param keyword 可选：编码、名称或分组包含该文本，最多 64 字符
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按 sortNo、keyTypeCode 升序，每项带 referencedTemplateCount
     */
    @GetExchange("/key-types")
    PagingResponseBody<QueryKeyTypeResponseBody> listQueryKeyTypes(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 新建键类型，状态 ACTIVE
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 编码、名称、分组、值类型、默认角色、入口设置与顺序号
     * @return 保存结果，objectType=QUERY_KEY_TYPE
     *         <li>编码已存在返回 400；valuePattern 不合法返回 422（field 为 valuePattern）。</li>
     */
    @PostExchange("/key-types")
    ConfigurationMutationResponseBody createQueryKeyType(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateQueryKeyTypeRequestBody request);

    /**
     * 修改键类型；编码不可修改
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param keyTypeId 键类型 ID
     * @param request 新内容与原版本
     * @return 保存结果，objectType=QUERY_KEY_TYPE
     *         <li>被引用时修改 valueType 返回 400；groupName、defaultRole 可改，只影响展示与编辑器默认值。</li>
     */
    @PutExchange("/key-types/{keyTypeId}")
    ConfigurationMutationResponseBody updateQueryKeyType(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("keyTypeId") String keyTypeId,
        @Valid @RequestBody UpdateQueryKeyTypeRequestBody request);

    /**
     * 启用或停用键类型
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param keyTypeId 键类型 ID
     * @param request 目标状态与原版本
     * @return 保存结果，objectType=QUERY_KEY_TYPE
     *         <li>停用时被 ACTIVE 模板当前版本引用返回 400。目标状态与当前相同时不递增版本。</li>
     */
    @PostExchange("/key-types/{keyTypeId}/status")
    ConfigurationMutationResponseBody changeQueryKeyTypeStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("keyTypeId") String keyTypeId,
        @Valid @RequestBody ChangeQueryKeyTypeStatusRequestBody request);

    /**
     * 保存全部键类型的新顺序
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 全部键类型按新顺序排列及各自原版本
     * @return 每个键类型的新 sortNo 与新版本
     *         <li>一个事务内按位置重写 sortNo = 序号 × 10；任一版本不符返回 409，整体不生效。</li>
     *         <li>items 未恰好覆盖全部键类型或有重复返回 400。</li>
     */
    @PutExchange("/key-type-order")
    ReorderQueryKeyTypesResponseBody reorderQueryKeyTypes(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody ReorderQueryKeyTypesRequestBody request);
}
