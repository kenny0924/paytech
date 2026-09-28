package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domain.operations.AssignOperationsRolesContext;
import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domain.operations.DeleteOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountStateContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRefreshRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSessionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAccountService;

import lombok.AllArgsConstructor;

/** 只编排所属表的保存，不查询资格、不调用其他 Service 或外部依赖。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAccountServiceImpl implements OperationsAccountService {
    private final IdGenerator ids;
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsRefreshRepository refreshes;
    private final OperationsSessionRepository sessions;
    private final OperationsPasswordRepository passwords;
    private final OperationsMemberRoleRepository grants;

    /** 原子保存账号、凭据、成员和初始角色。 */
    @Override
    public void createAccount(CreateOperationsAccountContext context) {
        var now = context.getNow();
        accounts.insert(context.getAccount(), now);
        passwords.insert(context.getPassword(), now);
        members.insert(context.getMember(), now);
        context.getRoles().forEach(role -> grants.grant(ids.nextId(), context.getMember(), role, context.getActorId(), now));
    }

    /** 停用账号与撤销全部会话必须一起提交。 */
    @Override
    public void disableAccount(OperationsAccountStateContext context) {
        var now = context.getNow();
        accounts.disable(context.getAccount(), context.getMember(), now);
        sessions.revokeAccount(context.getAccount().id(), "ACCOUNT_DISABLED", now);
        refreshes.revokeAccount(context.getAccount().id(), now);
    }

    /** 恢复账号但不恢复旧会话或重置密码状态。 */
    @Override
    public void enableAccount(OperationsAccountStateContext context) {
        accounts.enable(context.getAccount(), context.getMember(), context.getNow());
    }

    /** 关闭账号并撤销成员、授权、密码和全部会话，保留历史。 */
    @Override
    public void deleteAccount(DeleteOperationsAccountContext context) {
        var now = context.getNow();
        accounts.close(context.getAccount(), now);
        members.revoke(context.getMember(), now);
        grants.revoke(context.getMember().id(), now);
        if (context.getPassword() != null) {
            passwords.revoke(context.getPassword(), now);
        }
        sessions.revokeAccount(context.getAccount().id(), "ACCOUNT_DELETED", now);
        refreshes.revokeAccount(context.getAccount().id(), now);
    }

    /** 成员 CAS 成功后替换角色，原对象在 SQL 成功后同步版本。 */
    @Override
    public void assignRoles(AssignOperationsRolesContext context) {
        var now = context.getNow();
        members.advance(context.getMember(), now);
        grants.revoke(context.getMember().id(), now);
        context.getRoles().forEach(role -> grants.replace(ids.nextId(), context.getMember(), role, context.getActorId(), now));
    }
}
