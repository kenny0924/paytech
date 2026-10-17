package com.paytech.platform.support.api.permissionmanagement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.permissionmanagement.request.ChangePermissionStatusRequestBody;
import com.paytech.platform.support.api.permissionmanagement.request.CreatePermissionRequestBody;
import com.paytech.platform.support.api.permissionmanagement.request.UpdatePermissionRequestBody;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionOption;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionReference;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionResponseBody;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionSummary;

import jakarta.validation.Valid;

/** 权限目录管理入口；服务端验证当前权限，请求幂等由 Gateway 负责。 */
@HttpExchange("/permission-admin/v1")
public interface PermissionManagementApi {
    /**
     * 分页查询全局权限目录。
     * <p>
     * 可读取未绑定、停用及非 Operations 引用的权限；结果不代表操作者的授权集合。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param page
     *            页码，默认 1，范围 1..100000
     * @param size
     *            每页条数，默认 10，仅支持 10、20、50、100
     * @param code
     *            权限编码精确筛选，可空
     * @param resourceType
     *            资源类型编码精确筛选，可空
     * @param status
     *            ACTIVE 或 INACTIVE 筛选，可空
     * @param riskLevel
     *            LOW、MEDIUM、HIGH 或 CRITICAL 筛选，可空
     * @return 授权范围内的当前查询结果
     */
    @GetExchange("/permissions")
    PagingResponseBody<PermissionSummary> permissions(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "riskLevel", required = false) String riskLevel);

    /**
     * 查询权限详情、维护边界与引用计数。
     * <p>
     * 引用计数反映当前配置，不承诺跨表快照；权限不存在返回 NOT_FOUND。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param permissionCode
     *            不可变权限编码
     * @return 授权范围内的当前查询结果
     */
    @GetExchange("/permissions/{permissionCode}")
    PermissionResponseBody permission(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("permissionCode") String permissionCode);

    /**
     * 创建初始状态为 INACTIVE 的权限。
     * <p>
     * 权限编码及资源类型、动作身份不可变；不自动授予角色或成员，成功返回 HTTP 200。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param request
     *            不可变权限身份、风险等级及创建原因
     * @return 当前配置保存结果，不代表 Gateway 已加载
     */
    @PostExchange("/permissions")
    ConfigurationMutationResponseBody createPermission(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreatePermissionRequestBody request);

    /**
     * 按原版本更新权限风险等级。
     * <p>
     * 身份与状态保持；无变化不递增版本或重复记录审计，版本不符返回并发冲突。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param permissionCode
     *            不可变权限编码
     * @param request
     *            目标风险等级、原版本及更新原因
     * @return 当前配置保存结果，不代表 Gateway 已加载
     */
    @PutExchange("/permissions/{permissionCode}")
    ConfigurationMutationResponseBody updatePermission(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("permissionCode") String permissionCode,
        @Valid @RequestBody UpdatePermissionRequestBody request);

    /**
     * 按原版本启用或停用权限。
     * <p>
     * 保留资源、路由及角色关联；受保护管理入口引用的权限不可停用，相同状态请求被拒绝。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param permissionCode
     *            不可变权限编码
     * @param request
     *            目标状态、原版本及变更原因
     * @return 当前配置保存结果，不代表 Gateway 已加载
     */
    @PatchExchange("/permissions/{permissionCode}/status")
    ConfigurationMutationResponseBody changePermissionStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("permissionCode") String permissionCode,
        @Valid @RequestBody ChangePermissionStatusRequestBody request);

    /**
     * 分页查询指定类别的权限引用。
     * <p>
     * 每页仅返回一种配置引用；角色只返回未撤销的授权关联，不展开人员或凭据。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param permissionCode
     *            不可变权限编码
     * @param kind
     *            唯一引用类别：RESOURCE、ROUTE 或 ROLE
     * @param page
     *            页码，默认 1，范围 1..100000
     * @param size
     *            每页条数，默认 10，仅支持 10、20、50、100
     * @return 授权范围内的当前查询结果
     */
    @GetExchange("/permissions/{permissionCode}/references")
    PagingResponseBody<PermissionReference> references(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("permissionCode") String permissionCode,
        @RequestParam(value = "kind", required = true) String kind,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 按使用场景查询全局 ACTIVE 权限选项。
     * <p>
     * GATEWAY_ROUTE 要求 GATEWAY_CONFIG_READ；CONSOLE_RESOURCE 要求 CONSOLE_RESOURCE_READ。
     * </p>
     *
     * @param actor
     *            Gateway 传递的可信操作人账号
     * @param session
     *            Gateway 传递的可信会话标识
     * @param usage
     *            使用场景：GATEWAY_ROUTE 或 CONSOLE_RESOURCE
     * @param page
     *            页码，默认 1，范围 1..100000
     * @param size
     *            每页条数，默认 10，仅支持 10、20、50、100
     * @param keyword
     *            编码、资源类型或动作的普通文本包含筛选，最多 128 字符，可空
     * @return 授权范围内的当前查询结果
     */
    @GetExchange("/permission-options")
    PagingResponseBody<PermissionOption> options(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "usage", required = true) String usage,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword);

}
