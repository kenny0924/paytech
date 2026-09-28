package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.domain.authentication.*;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientRefreshRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientSessionRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import lombok.AllArgsConstructor;

/** 仅保存已核对的本库事实，不调用其他 Service 或外部依赖。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class CustomerAuthenticationServiceImpl implements CustomerAuthenticationService {
    private final Clock clock;
    private final CustomerAccountRepository accounts;
    private final CustomerPasswordRepository passwords;
    private final ClientSessionRepository sessions;
    private final ClientRefreshRepository refreshes;

    /** 密码失败计数提交后，由用例入口返回认证拒绝。 */
    @Override
    public void passwordFailure(LoginClientContext context) {
        accounts.passwordFailure(context.getAccount(), context.getNow());
    }

    /** 清除失败计数并保存新会话及首个刷新代。 */
    @Override
    public void login(LoginClientContext context) {
        accounts.passwordSuccess(context.getAccount(), context.getNow());
        sessions.insertSession(context.getPlan().issue());
        refreshes.insertRefresh(context.getPlan().issue());
    }

    /** 原子消费旧代、保存后继并推进当前会话。 */
    @Override
    public void refresh(RefreshClientContext context) {
        var issue = context.getPlan().issue();
        refreshes.consumeRefresh(context.getRefresh(), context.getNow());
        refreshes.insertRefresh(issue);
        refreshes.linkRefresh(context.getRefresh(), issue.refreshId());
        sessions.advanceSession(context.getSession(), issue);
    }

    /** 正确旧令牌重用时撤销会话与剩余活动刷新代。 */
    @Override
    public void compromise(RefreshClientContext context) {
        sessions.revokeSession(context.getSession(), OperationsSessionStatus.COMPROMISED, "REFRESH_REUSE",
            context.getNow());
        refreshes.revokeRefresh(context.getSession().id(), context.getNow());
        if (context.getRefresh().status() == OperationsRefreshStatus.ISSUED) {
            context.getRefresh().status(OperationsRefreshStatus.REVOKED);
        }
    }

    /** 账号安全纪元或会话失效时撤销其活动刷新代。 */
    @Override
    public void invalidate(RefreshClientContext context) {
        sessions.revokeSession(context.getSession(), OperationsSessionStatus.REVOKED, "SESSION_INVALID",
            context.getNow());
        refreshes.revokeRefresh(context.getSession().id(), context.getNow());
        if (context.getRefresh().status() == OperationsRefreshStatus.ISSUED) {
            context.getRefresh().status(OperationsRefreshStatus.REVOKED);
        }
    }

    /** 退出当前会话；已结束会话保持原终态。 */
    @Override
    public void logout(LogoutClientContext context) {
        sessions.revokeSession(context.getSession(), OperationsSessionStatus.REVOKED, "LOGOUT", context.getNow());
        refreshes.revokeRefresh(context.getSession().id(), context.getNow());
        if (context.getRefresh().status() == OperationsRefreshStatus.ISSUED) {
            context.getRefresh().status(OperationsRefreshStatus.REVOKED);
        }
    }

    /** 供现有跨模块消费者读取当前有效的账号上下文。 */
    @Override
    public ClientAccountView context(long accountId, long sessionId) {
        var account = accounts.account(accountId);
        var session = sessions.session(sessionId);
        if (!ClientAccountPolicy.valid(account, passwords.password(accountId), session, clock.instant())) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return ClientAccountPolicy.toContext(account, session);
    }
}
