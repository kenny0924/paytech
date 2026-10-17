package com.paytech.platform.support.api.consolemanagement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.*;

import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.consolemanagement.request.ChangeConsoleResourceStatusRequestBody;
import com.paytech.platform.support.api.consolemanagement.request.CreateConsoleResourceRequestBody;
import com.paytech.platform.support.api.consolemanagement.request.UpdateConsoleResourceRequestBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceCapabilitiesResponseBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceResponseBody;
import com.paytech.platform.support.api.consolemanagement.response.ConsoleResourcesResponseBody;

import jakarta.validation.Valid;

/**
 * Operations 控制台资源管理的内部 HTTP 合同。
 *
 * <p>每次调用均校验当前普通 Operations 会话及对应读写权限；请求幂等职责归 Gateway。
 * 身份失效返回 OPERATIONS_SESSION_INVALID，权限不足返回 OPERATIONS_PERMISSION_DENIED。
 * 保存成功只表示 Platform 当前配置已保存，不代表已授予人员权限或浏览器已刷新导航。</p>
 */
@HttpExchange("/console-admin/v1")
public interface ConsoleResourceManagementApi {
    /**
     * 查询完整 Operations 资源目录。
     *
     * <p>包含停用资源和当前制品不支持的历史组件，并返回维护边界及支持标记。
     * 不分页；超过 2000 条时返回 BUSINESS_CHECK_FAILED，不截断树。</p>
     *
     * @param actor 可信 Operations 操作者账号标识
     * @param session 当前普通 Operations 会话，要求 CONSOLE_RESOURCE_READ
     * @return 按资源主键升序排列的平面资源列表，parentResourceId 表达父子关系
     */
    @GetExchange("/resources")
    ConsoleResourcesResponseBody resources(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);

    /**
     * 查询单个 Operations 资源的当前详情。
     *
     * <p>停用资源和不受当前制品支持的历史组件仍可读取；返回值不表示当前操作者可执行该资源对应的业务操作。</p>
     *
     * @param actor 可信 Operations 操作者账号标识
     * @param session 当前普通 Operations 会话，要求 CONSOLE_RESOURCE_READ
     * @param resourceCode 精确资源编码；不存在或不属于 Operations 时返回 NOT_FOUND
     * @return 资源当前配置、绑定权限状态、维护边界、支持标记及创建和更新时间
     */
    @GetExchange("/resources/{resourceCode}")
    ConsoleResourceResponseBody resource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("resourceCode") String resourceCode);

    /**
     * 创建 Operations 菜单或按钮资源。
     *
     * <p>资源编码必须唯一，新绑定权限必须启用；父子结构、页面组件和按钮动作须符合当前部署能力。
     * 前端路径由组件派生，不接受任意路径。重复编码或非法业务关联返回 BUSINESS_CHECK_FAILED。</p>
     *
     * @param actor 可信 Operations 操作者账号标识
     * @param session 当前普通 Operations 会话，要求 CONSOLE_RESOURCE_MANAGE
     * @param request 资源编码、类型、展示名称、父级、组件、权限、排序及操作原因，字段约束见请求模型
     * @return HTTP 200 的保存回执，包含资源身份、初始 DISABLED 状态和版本 1；不自动授予人员权限
     */
    @PostExchange("/resources")
    ConfigurationMutationResponseBody createResource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateConsoleResourceRequestBody request);

    /**
     * 按期望版本更新资源的可变配置。
     *
     * <p>资源编码、Channel、类型及状态保持不变；无变化保存保留当前版本。
     * 内置管理资源只允许修改名称和排序，违反保护规则或树结构约束返回 BUSINESS_CHECK_FAILED。</p>
     *
     * @param actor 可信 Operations 操作者账号标识
     * @param session 当前普通 Operations 会话，要求 CONSOLE_RESOURCE_MANAGE
     * @param resourceCode 精确资源编码；不存在或不属于 Operations 时返回 NOT_FOUND
     * @param request 完整可变字段、expectedVersion 和操作原因；期望版本不匹配返回 CONCURRENT_REQUEST
     * @return HTTP 200 的保存回执；有变化时版本递增 1，无变化时返回原状态和版本
     */
    @PutExchange("/resources/{resourceCode}")
    ConfigurationMutationResponseBody updateResource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("resourceCode") String resourceCode,
        @Valid @RequestBody UpdateConsoleResourceRequestBody request);

    /**
     * 按期望版本启用或停用资源。
     *
     * <p>停用父资源保留子项自身状态及全部引用；启用前重新校验当前权限绑定、组件和树结构。
     * 内置管理资源不允许通过本接口启停；目标状态与当前相同或业务资格不满足时返回 BUSINESS_CHECK_FAILED。</p>
     *
     * @param actor 可信 Operations 操作者账号标识
     * @param session 当前普通 Operations 会话，要求 CONSOLE_RESOURCE_MANAGE
     * @param resourceCode 精确资源编码；不存在或不属于 Operations 时返回 NOT_FOUND
     * @param request 目标 ACTIVE/DISABLED 状态、expectedVersion 和操作原因；版本不匹配返回 CONCURRENT_REQUEST
     * @return HTTP 200 的保存回执，包含目标状态和递增 1 后的版本
     */
    @PatchExchange("/resources/{resourceCode}/status")
    ConfigurationMutationResponseBody changeResourceStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("resourceCode") String resourceCode,
        @Valid @RequestBody ChangeConsoleResourceStatusRequestBody request);

    /**
     * 查询当前制品支持的控制台资源能力。
     *
     * <p>能力来自随制品交付的页面及动作清单，不代表资源实例已经创建、启用或授予当前人员。</p>
     *
     * @param actor 可信 Operations 操作者账号标识
     * @param session 当前普通 Operations 会话，要求 CONSOLE_RESOURCE_READ
     * @return 已部署组件及按钮动作、支持的资源类型、最大树深度和完整资源数量上限
     */
    @GetExchange("/resource-capabilities")
    ConsoleResourceCapabilitiesResponseBody capabilities(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);

}
