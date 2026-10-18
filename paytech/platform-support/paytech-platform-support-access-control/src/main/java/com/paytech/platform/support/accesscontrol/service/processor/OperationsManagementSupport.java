package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountView;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleSummary;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsRoleView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.RoleSnapshot;

import lombok.AllArgsConstructor;

/** 人员与角色用例共用的事实读取及资格判断；保存由各用例 Service 负责。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsManagementSupport {
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsPasswordRepository passwords;
    private final OperationsDirectoryRepository directory;
    private final OperationsManagementPolicyService policy;

    /** 当前普通人员才可通过管理入口变更。 */
    OperationsAccount requireHuman(long id) {
        var account = accounts.account(id);
        if (account == null || account.type() != OperationsAccountType.HUMAN
            || account.status() == OperationsAccountStatus.CLOSED) {
            throw new BizCheckFailException("待操作的人员账号不存在或不允许此操作");
        }
        return account;
    }

    /** 目标成员必须仍属于当前 INTERNAL Tenant。 */
    OperationsMember requireMember(long accountId, long tenantId) {
        var member = members.member(accountId);
        if (member == null || member.tenantId() != tenantId) {
            throw new BizCheckFailException("人员账号没有当前 Operations 的成员关系");
        }
        return member;
    }

    /** 启用恢复原有授权，密码及成员安全资格必须仍有效。 */
    OperationsMember requireEnableableMember(OperationsAccount target, long tenantId) {
        if (target.status() != OperationsAccountStatus.DISABLED) {
            throw new BizCheckFailException("只能启用已停用的人员账号");
        }
        var member = requireMember(target.id(), tenantId);
        if (member.status() != OperationsMemberStatus.ACTIVE
            || member.ownerStatus() != OperationsTenantOwnerStatus.ACTIVE
            || member.securityStatus() != OperationsTenantSecurityStatus.ACTIVE) {
            throw new BizCheckFailException("人员账号没有当前 Operations 的有效成员关系，不能启用");
        }
        var password = passwords.password(target.id());
        if (password == null || password.status() != OperationsCredentialStatus.ACTIVE
            || password.credentialVersion() != target.credentialVersion()) {
            throw new BizCheckFailException("人员账号没有可用的密码凭据，不能启用");
        }
        return member;
    }

    /** 版本不符不覆盖原对象，也不读取新版本重试。 */
    void requireVersion(int actual, int expected) {
        if (actual != expected) {
            throw new ConcurrentRequestException("数据已被更新，请刷新后重试");
        }
    }

    /** 将已校验的角色 ID 解析为本次 Context 使用的角色对象。 */
    List<OperationsRole> roles(List<Long> ids) {
        return ids.stream().map(this::requireRole).toList();
    }

    /** 当前可见范围中不存在的角色不参与管理。 */
    OperationsRole requireRole(long roleId) {
        var role = directory.role(roleId);
        if (role == null) {
            throw new BizCheckFailException("角色不存在或不属于当前 Operations 范围");
        }
        return role;
    }

    /** 系统角色及 Platform 范围角色不可编辑或删除。 */
    void requireCustomRole(OperationsRole role) {
        if (role.systemRole() || role.platformRole()) {
            throw new BizCheckFailException("系统角色不允许修改或删除");
        }
    }

    /** 完整权限集合规范化且不能超出操作者授予上限。 */
    List<Long> permissionIds(List<String> values) {
        if (values == null) {
            throw new InvalidParameterException("权限列表不能为空，未授权角色请提交空列表");
        }
        var ids = values.stream().map(policy::parseId).distinct().sorted().toList();
        if (ids.size() != values.size()) {
            throw new InvalidParameterException("权限列表不能包含重复权限");
        }
        return ids;
    }

    /** 名称去除首尾空白，禁止空名和超长值。 */
    String roleName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 255) {
            throw new InvalidParameterException("角色名称不能为空且不能超过 255 个字符");
        }
        return name;
    }

    /** 批量计数必须覆盖所有请求角色，不能把范围外角色默认为零。 */
    Map<Long, Long> employeeCounts(List<Long> ids) {
        var counts = directory.roleEmployeeCounts(ids);
        if (counts.size() != ids.size()) {
            throw new BizCheckFailException("角色不存在或不属于当前 Operations 范围");
        }
        return counts;
    }

    /** 使用已有角色与权限组装结果，不为写响应重读数据库。 */
    OperationsRoleView roleDetails(OperationsRole role, List<Long> permissions) {
        return new OperationsRoleView(Long.toString(role.id()), role.code(), role.name(), role.systemRole(),
            role.version(), permissions.stream().map(String::valueOf).toList());
    }

    /** 使用已有人员 Context 的事实组装最小展示结果。 */
    OperationsAccountView accountDetails(CreateOperationsAccountContext context) {
        var account = context.getAccount();
        var member = context.getMember();
        return new OperationsAccountView(Long.toString(account.id()), account.emailMasked(), account.status().getCode(),
            context.getPassword().forceChange(), account.lockedUntil(), account.version(), Long.toString(member.id()),
            member.version(), context.getRoles().stream().sorted(java.util.Comparator.comparing(OperationsRole::code))
                .map(this::roleSummary).toList());
    }

    /** 角色摘要不暴露数据库对象。 */
    RoleSummary roleSummary(OperationsRole role) {
        return new RoleSummary(Long.toString(role.id()), role.code(), role.name(), role.systemRole(), role.version());
    }

    /** 类型化审计只保留批准的安全字段。 */
    RoleSnapshot auditRole(OperationsRoleView role) {
        return new RoleSnapshot(role.getRoleId(), role.getRoleCode(), role.getRoleName(), role.getVersion(),
            role.getPermissionIds());
    }
}
