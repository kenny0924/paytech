package com.paytech.platform.support.api.operations;

import java.time.Instant;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.operations.request.*;
import com.paytech.platform.support.api.operations.response.*;

import jakarta.validation.Valid;

/** Operations 人员和审计内部接口，所有标识以字符串传输以避免浏览器整数精度丢失。 */
@HttpExchange("/operations/v1")
public interface OperationsManagementApi {
    /**
     * 分页查询当前 Operations 人员；需要 OPERATIONS_ACCOUNT_READ，缺失必要成员或凭据事实时失败关闭。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param page
     *            页码，从 1 开始；为空默认 1
     * @param size
     *            每页条数，允许 10、20、50、100、200；为空默认 10
     * @return 分页结果，空页保留总数
     */
    @GetExchange("/accounts")
    PagingResponseBody<AccountResponseBody> accounts(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 创建强制首次改密的人员账号及初始角色；需要 OPERATIONS_ACCOUNT_CREATE，所授权限不得超过操作者上限。邮箱在 Realm 内唯一，请求重放由 Gateway 处理。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     * @return 当前业务结果，不包含敏感原值
     */
    @PostExchange("/accounts")
    AccountResponseBody createAccount(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody CreateAccountRequestBody request);

    /**
     * 停用普通人员并撤销全部会话；需要 OPERATIONS_ACCOUNT_DISABLE，不能停用本人或最后一个有效管理员。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param accountId
     *            目标 Operations 人员账号 ID
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     */
    @PostExchange("/accounts/{accountId}/disable")
    void disableAccount(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("accountId") String accountId,
        @Valid @RequestBody DisableAccountRequestBody request);

    /**
     * 恢复已停用人员账号；需要 OPERATIONS_ACCOUNT_ENABLE，重检原角色授予上限，保留成员、密码安全状态及已撤销会话。成功返回 204。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param accountId
     *            目标 Operations 人员账号 ID
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     */
    @PostExchange("/accounts/{accountId}/enable")
    void enableAccount(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("accountId") String accountId,
        @Valid @RequestBody EnableAccountRequestBody request);

    /**
     * 永久关闭人员并撤销成员、凭据、角色和会话；需要 OPERATIONS_ACCOUNT_DELETE，双版本保护，不能删除本人或最后一个有效管理员。保留历史，成功返回 204。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param accountId
     *            目标 Operations 人员账号 ID
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     */
    @DeleteExchange("/accounts/{accountId}")
    void deleteAccount(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("accountId") String accountId,
        @Valid @RequestBody DeleteAccountRequestBody request);

    /**
     * 按成员版本完整替换人员角色；需要 OPERATIONS_ACCOUNT_ASSIGN_ROLES，禁止超额授权及移除最后一个有效管理员。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param accountId
     *            目标 Operations 人员账号 ID
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     */
    @PutExchange("/accounts/{accountId}/roles")
    void assignRoles(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("accountId") String accountId,
        @Valid @RequestBody AssignRolesRequestBody request);

    /**
     * 分页查询当前有效且可分配的 Operations 角色；需要 OPERATIONS_ROLE_READ，范围限于 PLATFORM 与固定 INTERNAL Tenant。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param page
     *            页码，从 1 开始；为空默认 1
     * @param size
     *            每页条数，允许 10、20、50、100、200；为空默认 10
     * @return 分页结果，空页保留总数
     */
    @GetExchange("/roles")
    PagingResponseBody<RoleSummary> roles(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 批量查询角色关联人员数；需要 OPERATIONS_ROLE_READ，包含停用人员、暂停成员及未来生效关联。拒绝重复或范围外角色，零关联返回 0。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     * @return 当前业务结果，不包含敏感原值
     */
    @PostExchange("/roles/employee-counts")
    RoleEmployeeCountsResponseBody roleEmployeeCounts(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody RoleEmployeeCountsRequestBody request);

    /**
     * 逻辑删除无关联人员的自定义角色并撤销权限；需要 OPERATIONS_ROLE_DELETE，系统角色不可删除，expectedVersion 保护完整聚合。成功返回 204。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param roleId
     *            目标 Operations 角色 ID
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     */
    @DeleteExchange("/roles/{roleId}")
    void deleteRole(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("roleId") String roleId,
        @Valid @RequestBody DeleteRoleRequestBody request);

    /**
     * 查询角色与完整权限集合；需要 OPERATIONS_ROLE_UPDATE，目标权限必须处于操作者授予上限内。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param roleId
     *            目标 Operations 角色 ID
     * @return 当前业务结果，不包含敏感原值
     */
    @GetExchange("/roles/{roleId}")
    RoleResponseBody role(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("roleId") String roleId);

    /**
     * 创建当前 INTERNAL Tenant 自定义角色并原子保存权限；需要 OPERATIONS_ROLE_CREATE，仅能授予当前允许的 Operations 权限，角色代码不可复用。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     * @return 当前业务结果，不包含敏感原值
     */
    @PostExchange("/roles")
    RoleResponseBody createRole(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @Valid @RequestBody CreateRoleRequestBody request);

    /**
     * 按原版本原子替换自定义角色名称与完整权限集合；需要 OPERATIONS_ROLE_UPDATE，系统角色不可修改，旧授权及新授权均不能超过操作者上限。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param roleId
     *            目标 Operations 角色 ID
     * @param request
     *            请求字段；版本冲突返回公共并发错误，业务资格不符时拒绝操作
     * @return 当前业务结果，不包含敏感原值
     */
    @PutExchange("/roles/{roleId}")
    RoleResponseBody updateRole(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("roleId") String roleId,
        @Valid @RequestBody UpdateRoleRequestBody request);

    /**
     * 查询当前操作者可授予的有效权限目录；至少具有 OPERATIONS_ROLE_CREATE 或 OPERATIONS_ROLE_UPDATE。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @return 当前业务结果，不包含敏感原值
     */
    @GetExchange("/role-permissions")
    PermissionCatalogResponseBody permissions(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 查询可授予权限关联的平面菜单和按钮目录；至少具有 OPERATIONS_ROLE_CREATE 或 OPERATIONS_ROLE_UPDATE，页面树由 BFF 组装。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @return 当前业务结果，不包含敏感原值
     */
    @GetExchange("/role-resources")
    RoleResourcesResponseBody roleResources(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 分页查询 Operations 审计事实；需要 OPERATIONS_AUDIT_READ，可选字段精确匹配，时间范围为左闭右开区间。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param page
     *            页码，从 1 开始；为空默认 1
     * @param size
     *            每页条数，允许 10、20、50、100、200；为空默认 10
     * @param occurredFrom
     *            UTC 起点，包含该时刻；为空不限制
     * @param occurredTo
     *            UTC 终点，不包含该时刻；为空不限制
     * @param actorId
     *            可选操作者 ID，精确匹配
     * @param actionCode
     *            可选动作代码，精确匹配
     * @param result
     *            可选结果 Code，精确匹配
     * @param requestId
     *            可选请求追踪 ID，精确匹配
     * @param resourceType
     *            可选资源类型 Code，精确匹配
     * @param resourceId
     *            可选资源 ID，精确匹配
     * @return 分页结果，空页保留总数
     */
    @GetExchange("/audit-events")
    PagingResponseBody<AuditEvent> auditEvents(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "occurredFrom", required = false) Instant occurredFrom,
        @RequestParam(value = "occurredTo", required = false) Instant occurredTo,
        @RequestParam(value = "actorId", required = false) String actorId,
        @RequestParam(value = "actionCode", required = false) String actionCode,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "requestId", required = false) String requestId,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "resourceId", required = false) String resourceId);

    /**
     * 查询 Operations 范围内的审计详情；需要 OPERATIONS_AUDIT_READ，按分类仅返回允许展示的属性，保留顺序和重复键。
     *
     * @param actorAccountId
     *            可信 Gateway 操作者账号 ID；执行时重检当前状态与权限
     * @param sessionId
     *            可信 Gateway 会话 ID；必须是当前普通 Operations 会话
     * @param auditEventId
     *            目标审计事件 ID
     * @return 当前业务结果，不包含敏感原值
     */
    @GetExchange("/audit-events/{auditEventId}")
    AuditEventResponseBody auditEvent(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("auditEventId") String auditEventId);
}
