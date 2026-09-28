package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.authentication.ClientAccountPolicy.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.domain.authentication.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.*;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.*;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;

/** 登录和刷新共享的事务外准备及提交前事实核对，不执行写入。 */
@Component
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientSessionPreparation {
    private final Clock clock;
    private final IdGenerator ids;
    private final LoginEmailVault vault;
    private final ClientTokenPreparation tokens;
    private final ClientRefreshRepository refreshes;
    private final ClientSessionRepository sessions;
    private final OperationsPasswordPolicy passwords;
    private final CustomerAccountRepository accounts;
    private final ClientSigningKeyRepository keys;
    private final CustomerPasswordRepository credentials;
    private final HumanAuthenticationCryptography crypto;

    /** 使用 CUSTOMER 专用端口，所有外部准备由用例在事务外调用。 */
    public ClientSessionPreparation(Clock clock, IdGenerator ids,
        @Qualifier("clientLoginEmailVault") LoginEmailVault vault, ClientTokenPreparation tokens,
        ClientRefreshRepository refreshes, ClientSessionRepository sessions, OperationsPasswordPolicy passwords,
        CustomerAccountRepository accounts, ClientSigningKeyRepository keys, CustomerPasswordRepository credentials,
        @Qualifier("clientAuthenticationCryptography") HumanAuthenticationCryptography crypto) {
        this.clock = clock;
        this.ids = ids;
        this.vault = vault;
        this.tokens = tokens;
        this.refreshes = refreshes;
        this.sessions = sessions;
        this.passwords = passwords;
        this.accounts = accounts;
        this.keys = keys;
        this.credentials = credentials;
        this.crypto = crypto;
    }

    /** 未知和格式错误的邮箱不跳过密码计算，也不返回账号存在性。 */
    Long resolveLogin(String email, String password) {
        String canonical;
        try {
            canonical = passwords.canonicalEmail(email);
        } catch (FailException invalid) {
            passwords.matchesUnknownAccount(password);
            return null;
        }
        var account = accounts.accountByEmail(vault.protectCanonicalEmail(canonical));
        if (account == null) {
            passwords.matchesUnknownAccount(password);
            return null;
        }
        return account.id();
    }

    /** 仅 ACTIVE、已验证且未锁定的账号能准备新会话。 */
    LoginPlan prepareLogin(long accountId, String password) {
        var account = accounts.account(accountId);
        if (account == null) {
            passwords.matchesUnknownAccount(password);
            return null;
        }
        var credential = credentials.password(accountId);
        Instant now = clock.instant();
        if (!eligible(account, credential) || account.lockedUntil() != null && now.isBefore(account.lockedUntil())) {
            passwords.matchesUnknownAccount(password);
            return loginPlan(account, false, null);
        }
        validateCredential(credential);
        if (!passwords.matches(password, credential.verifier())) {
            return loginPlan(account, true, null);
        }
        ClientAccountView context = new ClientAccountView(account.id(), ids.nextId(), account.emailMasked(), true,
            "ACCOUNT", null, null, List.of("ONBOARDING", "LOGOUT"));
        Issue issue = tokens.prepare(context, account.authEpoch(), account.credentialVersion(),
            ClientSessionRules.absoluteExpiry(now), 1);
        return loginPlan(account, false, issue);
    }

    /** 把同一账号的准备结果投影为跨模块业务命令。 */
    LoginPlan loginPlan(CustomerAccount account, boolean mismatch, Issue issue) {
        return new LoginPlan(account.id(), account.version(), account.credentialVersion(), account.authEpoch(),
            mismatch, issue);
    }

    /** 格式和 CLIENT 归属均受限，公开查找段不等于持有刷新秘密。 */
    RefreshIdentity resolveRefresh(String token) {
        String lookup = ClientSessionRules.lookup(token);
        if (lookup == null) {
            return null;
        }
        var refresh = refreshes.refresh(lookup);
        var session = refresh == null ? null : sessions.session(refresh.sessionId());
        return session == null ? null : new RefreshIdentity(session.accountId(), session.id(), lookup);
    }

    /** 正确的已消费旧代也保留证明，让提交阶段执行会话重用处置。 */
    RefreshPlan prepareRefresh(RefreshIdentity identity, String token) {
        if (identity == null || !identity.lookup().equals(ClientSessionRules.lookup(token))) {
            return null;
        }
        var hmac = crypto.versionedHmac("client-refresh-v1", token);
        var account = accounts.account(identity.accountId());
        var session = sessions.session(identity.sessionId());
        var refresh = refreshes.refresh(identity.lookup());
        if (refresh == null || session == null || session.accountId() != identity.accountId()
            || refresh.sessionId() != session.id()) {
            return null;
        }
        if (hmac.keyVersion() != refresh.keyVersion()) {
            throw new ServiceUnavailableException();
        }
        Issue issue = null;
        if (matches(hmac.digest(), refresh.secretHmac()) && refresh.status() == OperationsRefreshStatus.ISSUED
            && valid(account, credentials.password(identity.accountId()), session, clock.instant())
            && refresh.generationNo() == session.generationNo() && clock.instant().isBefore(refresh.expiresTime())) {
            issue = tokens.prepare(toContext(account, session), account.authEpoch(), account.credentialVersion(),
                session.absoluteExpireTime(), Math.addExact(session.generationNo(), 1));
        }
        return new RefreshPlan(identity.accountId(), identity.sessionId(), identity.lookup(), hmac.digest(),
            hmac.keyVersion(), session.version(), issue);
    }

    /** 退出还要求当前 Access 上下文与 Cookie 对应同一会话。 */
    LogoutProof prepareLogout(long accountId, long sessionId, String token) {
        String lookup = ClientSessionRules.lookup(token);
        if (lookup == null) {
            return null;
        }
        var proof = crypto.versionedHmac("client-refresh-v1", token);
        return new LogoutProof(accountId, sessionId, lookup, proof.digest(), proof.keyVersion());
    }

    /** 签发准备之后发生密钥切换时拒绝落库，不能接纳已经失去签名授权的令牌。 */
    void requireCurrentSigningKey(Issue issue, Instant now) {
        var key = keys.signingKey();
        if (key == null || key.id() != issue.signingKeyId() || key.version() != issue.signingKeyVersion()
            || key.notBefore().isAfter(now) || !now.isBefore(issue.tokens().expiresAt())) {
            throw new ServiceUnavailableException();
        }
    }

    /** 在账号行锁下核对密码计算时的版本，避免接纳已被重置的密码。 */
    LoginClientContext currentLogin(LoginPlan plan) {
        if (plan == null) {
            return null;
        }
        CustomerAccount account = accounts.lockAccount(plan.accountId());
        if (account == null || account.version() != plan.expectedVersion()
            || account.credentialVersion() != plan.expectedCredentialVersion()
            || account.authEpoch() != plan.expectedEpoch()) {
            return null;
        }
        return new LoginClientContext(plan, account, clock.instant());
    }

    /** 当前账号与密码资格需要在提交签发结果前重证。 */
    boolean eligibleLogin(LoginClientContext context) {
        return eligible(context.getAccount(), credentials.password(context.getAccount().id()));
    }

    /** 按账号、会话、刷新代的固定锁序读取当前事实。 */
    RefreshClientContext currentRefresh(RefreshPlan plan) {
        if (plan == null) {
            return null;
        }
        CustomerAccount account = accounts.lockAccount(plan.accountId());
        ClientSession session = sessions.lockSession(plan.sessionId());
        ClientRefresh refresh = refreshes.lockRefresh(plan.lookup());
        if (session == null || refresh == null || session.accountId() != plan.accountId()
            || refresh.sessionId() != session.id() || refresh.keyVersion() != plan.keyVersion()
            || !matches(plan.computedHmac(), refresh.secretHmac())) {
            return null;
        }
        return new RefreshClientContext(plan, account, session, refresh, clock.instant());
    }

    /** 会话安全纪元、凭据版本和截止时间仍以提交时的数据库事实为准。 */
    boolean validRefresh(RefreshClientContext context) {
        return valid(context.getAccount(), credentials.password(context.getPlan().accountId()), context.getSession(),
            context.getNow());
    }

    /** 退出证明必须同时匹配可信账号、会话和 Cookie 秘密。 */
    LogoutClientContext currentLogout(LogoutProof proof) {
        if (proof == null) {
            return null;
        }
        accounts.lockAccount(proof.accountId());
        ClientSession session = sessions.lockSession(proof.sessionId());
        ClientRefresh refresh = refreshes.lockRefresh(proof.lookup());
        if (session == null || refresh == null || session.accountId() != proof.accountId()
            || refresh.sessionId() != session.id() || refresh.keyVersion() != proof.keyVersion()
            || !matches(proof.computedHmac(), refresh.secretHmac())) {
            return null;
        }
        return new LogoutClientContext(proof, session, refresh, clock.instant());
    }
}
