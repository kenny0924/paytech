package com.paytech.operations.console.bff.integration;

import java.util.List;
import java.util.Map;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditEventDetails;
import com.paytech.operations.console.bff.service.model.AuditModels.AuditQueryRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.AccountCreatedResponseBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.ChangePasswordRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.FirstAdministratorRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.LoginRequestBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.SessionContextResponseBody;
import com.paytech.operations.console.bff.service.model.AuthenticationModels.TokenGrantResponseBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AccountResponseBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AssignRolesRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.AuditEvent;
import com.paytech.operations.console.bff.service.model.ManagementModels.CreateAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.DeleteAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.DisableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.ManagementModels.EnableAccountRequestBody;
import com.paytech.operations.console.bff.service.model.NavigationModels.NavigationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RoleModels.CreateRoleRequestBody;
import com.paytech.operations.console.bff.service.model.RoleModels.DeleteRoleRequestBody;
import com.paytech.operations.console.bff.service.model.RoleModels.GrantablePermission;
import com.paytech.operations.console.bff.service.model.RoleModels.PermissionResource;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleOverview;
import com.paytech.operations.console.bff.service.model.RoleModels.RoleResponseBody;
import com.paytech.operations.console.bff.service.model.RoleModels.UpdateRoleRequestBody;

/**
 * Platform 出站端口只发布 BFF 本地模型，远端 DTO 与 HTTP 异常由实现隔离。
 *
 * @author Codex
 */
public interface PlatformOperationsClient {
    /** 调用 PLT-I02；拒绝或无有效授权结果时抛出公共异常，调用者不得继续业务调用。 */
    void authorize(OperatorContext context);

    /** 提交登录凭据并返回浏览器持有的一组 Token。 */
    TokenGrantResponseBody login(LoginRequestBody request);

    /** 消费当前 Refresh 并返回下一代 Token。 */
    TokenGrantResponseBody refresh(String refreshToken);

    /** 撤销操作人的当前 Session。 */
    void logout(OperatorContext context, String refreshToken);

    /** 查询操作人的当前身份与认证状态。 */
    SessionContextResponseBody context(OperatorContext context);

    /** 修改本人密码，由 Platform 撤销关联 Session。 */
    void changePassword(OperatorContext context, ChangePasswordRequestBody request);

    /** 完成 Bootstrap 到首位 Email 管理员的交接。 */
    AccountCreatedResponseBody createFirstAdministrator(OperatorContext context, FirstAdministratorRequestBody request);

    /** 查询 Platform 已按当前权限筛选的菜单和按钮。 */
    NavigationResponseBody navigation(OperatorContext context);

    /** 按页查询内部人员。 */
    PagingResponseBody<AccountResponseBody> accounts(OperatorContext context, int page, int size);

    /** 按页查询当前可分配角色。 */
    PagingResponseBody<RoleOverview> roles(OperatorContext context, int page, int size);

    /** 批量查询当前页各角色关联员工数；不得缺项、重复或将异常统计当作零。 */
    Map<String, Long> roleEmployeeCounts(OperatorContext context, List<String> roleIds);

    /** 查询当前操作人可管理的角色详情。 */
    RoleResponseBody role(OperatorContext context, String roleId);

    /** 查询 Platform 裁剪后的可授予权限，供页面组装。 */
    List<GrantablePermission> permissions(OperatorContext context);

    /** 查询授权范围内的 Console Resource 与 Permission 绑定。 */
    List<PermissionResource> roleResources(OperatorContext context);

    /** 使用原幂等键向 Platform 提交角色创建及权限集合。 */
    RoleResponseBody createRole(OperatorContext context, CreateRoleRequestBody request);

    /** 使用原幂等键与期望版本提交角色名称及完整权限集合。 */
    RoleResponseBody updateRole(OperatorContext context, String roleId, UpdateRoleRequestBody request);

    /** 使用原幂等键与期望版本提交角色删除；业务限制与事务由 Platform 校验。 */
    void deleteRole(OperatorContext context, String roleId, DeleteRoleRequestBody request);

    /** 按页查询 Operations 审计事件。 */
    PagingResponseBody<AuditEvent> auditEvents(OperatorContext context, int page, int size, AuditQueryRequestBody query);

    /** 查询一条已授权的历史审计事实及有序属性，供详情页面组装。 */
    AuditEventDetails auditEvent(OperatorContext context, String auditEventId);

    /** 使用原幂等键向 Platform 提交人员创建命令。 */
    AccountResponseBody createAccount(OperatorContext context, CreateAccountRequestBody request);

    /** 使用原幂等键和期望版本提交人员停用命令。 */
    void disableAccount(OperatorContext context, String accountId, DisableAccountRequestBody request);

    /** 使用原幂等键和期望版本提交人员启用命令。 */
    void enableAccount(OperatorContext context, String accountId, EnableAccountRequestBody request);

    /** 使用原幂等键和账号、成员双版本提交人员删除命令。 */
    void deleteAccount(OperatorContext context, String accountId, DeleteAccountRequestBody request);

    /** 使用原幂等键和期望成员版本提交角色分配命令。 */
    void assignRoles(OperatorContext context, String accountId, AssignRolesRequestBody request);
}
