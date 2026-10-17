package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.domain.authentication.ChangeOperationsPasswordContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.FirstOperationsAdministratorContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.LoginOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.LogoutOperationsContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.RefreshOperationsContext;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsAuthenticationService;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsMemberRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRefreshRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsSessionRepository;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.AllArgsConstructor;

/** 接收已验证上下文，仅保存本地事实；不调用其他 Service。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAuthenticationServiceImpl implements OperationsAuthenticationService {
    private final Clock clock;
    private final OperationsMemberRoleRepository grants;
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsSessionRepository sessions;
    private final OperationsPasswordRepository passwords;
    private final OperationsRefreshRepository refreshes;

    /** 保存失败计数；调用者提交后才返回认证失败。 */
    @Override
    public void passwordFailure(OperationsAccount account, Instant now) {
        accounts.passwordFailure(account, now);
    }

    /** 原子接受密码、保存新会话及首个刷新代。 */
    @Override
    public void login(LoginOperationsContext context) {
        accounts.passwordSuccess(context.getAccount(), context.getNow());
        sessions.insert(context.getSession());
        refreshes.insert(context.getTokens().refresh(), context.getNow());
    }

    /** 单次消费旧刷新代，保存后继并推进当前会话。 */
    @Override
    public void refresh(RefreshOperationsContext context) {
        refreshes.consume(context.getRefresh(), context.getNow());
        refreshes.insert(context.getTokens().refresh(), context.getNow());
        refreshes.link(context.getRefresh(), context.getTokens().refresh().id());
        sessions.advance(context.getSession(), context.getNow());
    }

    /** 正确旧代重用时撤销整个会话 Family。 */
    @Override
    public void compromise(RefreshOperationsContext context) {
        sessions.revoke(context.getSession(), OperationsSessionStatus.COMPROMISED, "REFRESH_REUSE", context.getNow());
        refreshes.revoke(context.getRefresh(), context.getNow());
    }

    /** 撤销已失去账号或成员资格的会话。 */
    @Override
    public void invalidate(RefreshOperationsContext context) {
        sessions.revoke(context.getSession(), OperationsSessionStatus.REVOKED, "SESSION_INVALID", context.getNow());
        refreshes.revoke(context.getRefresh(), context.getNow());
    }

    /** 退出仅撤销当前会话，不恢复原终态。 */
    @Override
    public void logout(LogoutOperationsContext context) {
        sessions.revoke(context.getSession(), OperationsSessionStatus.REVOKED, "LOGOUT", context.getNow());
        refreshes.revoke(context.getRefresh(), context.getNow());
    }

    /** 原子更换密码、推进账号安全纪元并撤销全部会话。 */
    @Override
    public void changePassword(ChangeOperationsPasswordContext context) {
        var now = context.getNow();
        accounts.changePassword(context.getAccount(), now);
        passwords.changePassword(context.getPassword(), context.getNextVerifier(), now);
        sessions.revokeCurrent(context.getSession(), now);
        sessions.revokeAccount(context.getAccount().id(), "PASSWORD_CHANGED", now);
        refreshes.revokeAccount(context.getAccount().id(), now);
    }

    /** 原子建立首次管理员并永久关闭 Bootstrap。 */
    @Override
    public void createFirstAdministrator(FirstOperationsAdministratorContext context) {
        var now = context.getNow();
        accounts.insert(context.getAccount(), now);
        passwords.insert(context.getPassword(), now);
        members.insert(context.getMember(), now);
        grants.grant(context.getGrantId(), context.getMember(), context.getRole(), context.getBootstrap().id(), now);
        accounts.disableBootstrap(context.getBootstrap(), now);
        passwords.revoke(context.getBootstrapPassword(), now);
        sessions.revokeAccount(context.getBootstrapSession(), "BOOTSTRAP_COMPLETED", now);
        refreshes.revokeAccount(context.getBootstrap().id(), now);
    }

    /** 保留人员生命周期入口的本地账号会话撤销能力。 */
    @Override
    public void revokeAccountSessions(long accountId, String reason) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        sessions.revokeAccount(accountId, reason, now);
        refreshes.revokeAccount(accountId, now);
    }

}
