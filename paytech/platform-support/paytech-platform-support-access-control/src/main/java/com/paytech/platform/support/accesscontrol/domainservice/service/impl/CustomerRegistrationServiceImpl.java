package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.authentication.ConsumeClientEmailContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.IssueClientEmailContext;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.*;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerRegistrationService;

import lombok.AllArgsConstructor;

/** 仅保存已核对的本库事实，不调用其他 Service 或外部依赖。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class CustomerRegistrationServiceImpl implements CustomerRegistrationService {
    private final CustomerAccountRepository accounts;
    private final CustomerPasswordRepository passwords;
    private final ClientSessionRepository sessions;
    private final ClientRefreshRepository refreshes;
    private final EmailAuthenticationChallengeRepository challenges;

    /** 同一事务保存待验证账号、凭据和新挑战；替代原活动链接。 */
    @Override
    public void issue(IssueClientEmailContext context) {
        var plan = context.getPlan();
        var now = context.getNow();
        if (plan.newAccount()) {
            accounts.insertAccount(context.getAccount(), now);
            passwords.insertPassword(context.getPassword(), now);
        } else if (plan.identity().passwordVerifier() != null) {
            accounts.replacePendingAccount(context.getAccount(), now);
            passwords.resetPassword(context.getPassword(), plan.identity().passwordVerifier(), now);
        }
        if (context.getPrevious() != null && context.getPrevious().status() == ClientChallengeStatus.PENDING) {
            challenges.supersedeChallenge(context.getPrevious(), now);
        }
        challenges.insertChallenge(context.getChallenge());
    }

    /** 保存无效挑战的失败次数和必要终态。 */
    @Override
    public void denyProof(ConsumeClientEmailContext context) {
        challenges.failChallenge(context.getChallenge(), context.getNow());
    }

    /** 单次消费验证挑战，并激活同一邮箱账号。 */
    @Override
    public void verifyEmail(ConsumeClientEmailContext context) {
        challenges.consumeChallenge(context.getChallenge(), context.getNow());
        accounts.activateAccount(context.getAccount(), context.getNow());
    }

    /** 单次消费恢复挑战，原子替换密码并撤销账号全部会话。 */
    @Override
    public void resetPassword(ConsumeClientEmailContext context) {
        var now = context.getNow();
        challenges.consumeChallenge(context.getChallenge(), now);
        accounts.resetAccount(context.getAccount(), now);
        passwords.resetPassword(context.getPassword(), context.getProof().newPasswordVerifier(), now);
        sessions.revokeAccountSessions(context.getAccount().id(), now);
        refreshes.revokeAccountRefresh(context.getAccount().id(), now);
    }
}
