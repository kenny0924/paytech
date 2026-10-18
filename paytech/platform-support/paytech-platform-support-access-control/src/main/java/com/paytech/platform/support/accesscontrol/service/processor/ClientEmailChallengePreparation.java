package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.EmailAuthenticationChallenge;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.ChallengeDraft;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.EmailIdentity;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.IssuePlan;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.LinkProof;
import com.paytech.platform.support.accesscontrol.domain.authentication.ConsumeClientEmailContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.IssueClientEmailContext;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerPasswordRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.EmailAuthenticationChallengeRepository;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

/** 注册、重发与链接消费共享的材料准备；本类不写库。 */
@Component
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientEmailChallengePreparation {
    private static final String PROOF_PURPOSE = "client-email-challenge-v1";
    private final Clock clock;
    private final IdGenerator ids;
    private final LoginEmailVault vault;
    private final OperationsPasswordPolicy passwords;
    private final CustomerAccountRepository accounts;
    private final ClientEmailChallengePolicy policy;
    private final CustomerPasswordRepository passwordRepository;
    private final HumanAuthenticationCryptography crypto;
    private final EmailAuthenticationChallengeRepository challenges;

    /** 绑定 CUSTOMER 独立的邮箱和 HMAC 端口。 */
    public ClientEmailChallengePreparation(Clock clock, IdGenerator ids,
        @Qualifier("clientLoginEmailVault") LoginEmailVault vault, OperationsPasswordPolicy passwords,
        CustomerAccountRepository accounts, ClientEmailChallengePolicy policy,
        CustomerPasswordRepository passwordRepository,
        @Qualifier("clientAuthenticationCryptography") HumanAuthenticationCryptography crypto,
        EmailAuthenticationChallengeRepository challenges) {
        this.clock = clock;
        this.ids = ids;
        this.vault = vault;
        this.passwords = passwords;
        this.accounts = accounts;
        this.policy = policy;
        this.passwordRepository = passwordRepository;
        this.crypto = crypto;
        this.challenges = challenges;
    }

    /** 邮箱和密码的外部准备不进入数据库事务。 */
    EmailIdentity prepareIdentity(String email, String password) {
        String canonical = passwords.canonicalEmail(email);
        String verifier =
            password == null ? null : passwords.encode(passwords.validateNewPassword(password, canonical));
        String token = vault.protectCanonicalEmail(canonical);
        String resourceKey = crypto.versionedHmac("customer-email-lock-v1", token).digest();
        return new EmailIdentity(canonical, token, passwords.maskEmail(canonical), verifier, resourceKey);
    }

    /** 在范围锁内解析真实账号，冷却覆盖发送失败与已结束的旧链接。 */
    IssuePlan prepareIssue(EmailIdentity identity, ClientChallengePurpose purpose) {
        CustomerAccount account = accounts.accountByEmail(identity.emailToken());
        if (!canIssue(account, identity, purpose)) {
            return null;
        }
        long accountId = account == null ? ids.nextId() : account.id();
        EmailAuthenticationChallenge previous = challenges.latestChallenge(accountId, purpose);
        Instant now = clock.instant();
        if (!policy.canResend(previous == null ? null : previous.createTime(), now)) {
            return null;
        }
        var link = policy.newLink();
        String scope = policy.scopeHash(accountId, purpose, identity.emailToken());
        var hmac =
            crypto.versionedHmac(PROOF_PURPOSE, policy.proofInput(link.challengeNo(), purpose, scope, link.secret()));
        var challenge = new ChallengeDraft(ids.nextId(), accountId, purpose, scope, hmac.digest(), hmac.keyVersion(),
            policy.expiresAt(purpose, now), now, previous == null ? null : previous.id(),
            previous == null ? null : previous.version(), previous == null ? null : previous.status(), link);
        return new IssuePlan(identity, challenge, ids.nextId(), account == null,
            account == null ? 0 : account.version(), account == null ? 0 : account.credentialVersion());
    }

    /** 未验证注册可以重新发起，已验证账号的凭据永远不被公开注册覆盖。 */
    boolean canIssue(CustomerAccount account, EmailIdentity identity, ClientChallengePurpose purpose) {
        if (purpose == ClientChallengePurpose.PASSWORD_RESET) {
            return account != null && account.status() == OperationsAccountStatus.ACTIVE
                && account.verifiedTime() != null;
        }
        return account == null ? identity.passwordVerifier() != null
            : account.status() == OperationsAccountStatus.PENDING_VERIFICATION && account.verifiedTime() == null;
    }

    /** 固定账号邮箱与预期版本；并发创建由数据库唯一键兜底。 */
    boolean matchesIssueAccount(IssuePlan plan, CustomerAccount account) {
        if (plan.newAccount()) {
            return account == null && accounts.accountByEmail(plan.identity().emailToken()) == null;
        }
        return account != null && account.version() == plan.expectedAccountVersion()
            && account.credentialVersion() == plan.expectedCredentialVersion()
            && Objects.equals(account.emailToken(), plan.identity().emailToken())
            && canIssue(account, plan.identity(), plan.challenge().purpose());
    }

    /** 最近挑战必须仍是准备时的那一条，不跨并发签发覆盖别人的链接。 */
    boolean samePrevious(ChallengeDraft draft, EmailAuthenticationChallenge latest) {
        return latest == null ? draft.previousId() == null : Objects.equals(draft.previousId(), latest.id())
            && Objects.equals(draft.previousVersion(), latest.version());
    }

    /** 只有 HMAC 进入持久化投影，链接原值留在本次调用。 */
    EmailAuthenticationChallenge toRecord(IssuePlan plan) {
        var draft = plan.challenge();
        return new EmailAuthenticationChallenge(draft.id(), draft.link().challengeNo(), draft.accountId(),
            draft.purpose(), plan.identity().emailToken(), plan.identity().emailMasked(), draft.scopeHash(),
            draft.secretHmac(), draft.keyVersion(), draft.expiresAt(), 5, 0, ClientChallengeStatus.PENDING,
            draft.previousId(), 1, draft.issuedAt());
    }

    /** 在写事务前计算 HMAC 和密码 verifier，验证链接不自动创建会话。 */
    LinkProof prepareProof(String challengeNo, String secret, ClientChallengePurpose purpose, String newPassword) {
        if (!policy.validLink(challengeNo, secret)) {
            return null;
        }
        EmailAuthenticationChallenge challenge = challenges.challenge(challengeNo);
        if (challenge == null || challenge.purpose() != purpose) {
            return null;
        }
        String input = policy.proofInput(challengeNo, purpose, challenge.scopeHash(), secret);
        var hmac = crypto.versionedHmac(PROOF_PURPOSE, input);
        if (hmac.keyVersion() != challenge.keyVersion()) {
            throw new ServiceUnavailableException();
        }
        String verifier = policy.accepts(challenge.state(), purpose, hmac.digest(), hmac.keyVersion(), clock.instant())
            ? prepareResetPassword(challenge, newPassword) : null;
        return new LinkProof(challenge.id(), challenge.accountId(), challenge.version(), purpose, hmac.digest(),
            hmac.keyVersion(), verifier);
    }

    /** 重置密码保留邮箱相关弱密码拒绝；Reveal 仅用于当前请求中的策略校验。 */
    String prepareResetPassword(EmailAuthenticationChallenge challenge, String newPassword) {
        if (challenge.purpose() != ClientChallengePurpose.PASSWORD_RESET) {
            return null;
        }
        String canonical = vault.revealCanonicalEmail(challenge.emailToken());
        return passwords.encode(passwords.validateNewPassword(newPassword, canonical));
    }

    /** 旧邮箱、旧签发或错误用途不能改变当前账号。 */
    boolean matchesProofAccount(LinkProof proof, CustomerAccount account, EmailAuthenticationChallenge challenge) {
        if (account == null || challenge == null || challenge.accountId() != account.id()
            || challenge.version() != proof.expectedVersion() || challenge.purpose() != proof.purpose()
            || !Objects.equals(account.emailToken(), challenge.emailToken())) {
            return false;
        }
        boolean eligible = proof.purpose() == ClientChallengePurpose.SIGN_UP_EMAIL
            ? account.status() == OperationsAccountStatus.PENDING_VERIFICATION && account.verifiedTime() == null
            : account.status() == OperationsAccountStatus.ACTIVE && account.verifiedTime() != null;
        return eligible
            && challenge.scopeHash().equals(policy.scopeHash(account.id(), proof.purpose(), account.emailToken()));
    }

    /** 在账号行锁下重证签发快照，再一次构造保存上下文。 */
    IssueClientEmailContext currentIssue(IssuePlan plan) {
        Instant now = clock.instant();
        CustomerAccount account = accounts.lockAccount(plan.challenge().accountId());
        if (!matchesIssueAccount(plan, account) || !plan.challenge().expiresAt().isAfter(now)) {
            return null;
        }
        EmailAuthenticationChallenge previous =
            challenges.latestChallenge(plan.challenge().accountId(), plan.challenge().purpose());
        if (!samePrevious(plan.challenge(), previous)
            || !policy.canResend(previous == null ? null : previous.createTime(), now)) {
            return null;
        }
        CustomerPassword password;
        if (plan.newAccount()) {
            account = new CustomerAccount(plan.challenge().accountId(), plan.identity().emailToken(),
                plan.identity().emailMasked(), null, OperationsAccountStatus.PENDING_VERIFICATION, 1, 1, 1, 0, null);
            password = new CustomerPassword(plan.passwordId(), account.id(), plan.identity().passwordVerifier(), 19,
                19456, 2, 1, 1, false, OperationsCredentialStatus.ACTIVE);
        } else {
            password = passwordRepository.password(account.id());
            if (plan.identity().passwordVerifier() != null
                && (password == null || password.credentialVersion() != account.credentialVersion())) {
                throw new ConcurrentRequestException("账号凭据已变化");
            }
        }
        return new IssueClientEmailContext(plan, account, password, previous, toRecord(plan), now);
    }

    /** 账号先于挑战加锁；无效证明的尝试次数由调用方决定并提交。 */
    ConsumeClientEmailContext currentProof(LinkProof proof) {
        CustomerAccount account = accounts.lockAccount(proof.accountId());
        EmailAuthenticationChallenge challenge = challenges.lockChallenge(proof.challengeId());
        if (!matchesProofAccount(proof, account, challenge)) {
            return null;
        }
        CustomerPassword password =
            proof.purpose() == ClientChallengePurpose.PASSWORD_RESET ? passwordRepository.password(account.id()) : null;
        if (proof.purpose() == ClientChallengePurpose.PASSWORD_RESET
            && (password == null || password.credentialVersion() != account.credentialVersion())) {
            return null;
        }
        return new ConsumeClientEmailContext(proof, account, password, challenge, clock.instant());
    }

    /** 判断当前挑战能否消费；只使用已加载的事实。 */
    boolean accepts(ConsumeClientEmailContext context) {
        LinkProof proof = context.getProof();
        return policy.accepts(context.getChallenge().state(), proof.purpose(), proof.computedHmac(), proof.keyVersion(),
            context.getNow());
    }
}
