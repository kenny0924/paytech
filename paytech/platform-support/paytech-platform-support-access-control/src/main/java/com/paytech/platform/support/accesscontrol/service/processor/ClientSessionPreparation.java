package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.authentication.ClientAccountPolicy.*;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;
import com.paytech.platform.support.accesscontrol.domain.authentication.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.*;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
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
    private final ClientMemberRepository members;

    /** 使用 CUSTOMER 专用端口，所有外部准备由用例在事务外调用。 */
    public ClientSessionPreparation(Clock clock, IdGenerator ids,
        @Qualifier("clientLoginEmailVault") LoginEmailVault vault, ClientTokenPreparation tokens,
        ClientRefreshRepository refreshes, ClientSessionRepository sessions, OperationsPasswordPolicy passwords,
        CustomerAccountRepository accounts, ClientSigningKeyRepository keys, CustomerPasswordRepository credentials,
        @Qualifier("clientAuthenticationCryptography") HumanAuthenticationCryptography crypto,
        ClientMemberRepository members) {
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
        this.members = members;
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
        ClientAccountView context = accountContext(account, ids.nextId(), null);
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
        Issue fallback = null;
        if (matches(hmac.digest(), refresh.secretHmac()) && refresh.status() == OperationsRefreshStatus.ISSUED
            && refresh.generationNo() == session.generationNo() && clock.instant().isBefore(refresh.expiresTime())) {
            var password = credentials.password(identity.accountId());
            ClientMember member = boundMemberOf(session);
            if (validContext(account, password, session, member, clock.instant())) {
                issue = tokens.prepare(toContext(account, session), account.authEpoch(), account.credentialVersion(),
                    session.absoluteExpireTime(), Math.addExact(session.generationNo(), 1));
            } else if (fallbackCandidate(account, password, session, member, clock.instant())) {
                fallback = tokens.prepare(accountContext(account, ids.nextId(), reasonOf(session, member)),
                    account.authEpoch(), account.credentialVersion(), session.absoluteExpireTime(), 1);
            }
        }
        return new RefreshPlan(identity.accountId(), identity.sessionId(), identity.lookup(), hmac.digest(),
            hmac.keyVersion(), session.version(), issue, fallback);
    }

    /** 租户会话绑定的成员；账号上下文为空。 */
    ClientMember boundMemberOf(ClientSession session) {
        return session.contextType() == ClientSessionContextType.TENANT && session.memberId() != null
            ? members.member(session.memberId()) : null;
    }

    /**
     * 租户会话可以降级为账号上下文
     * <li>账号与会话本身仍有效，只是会话绑定的成员或租户当前不允许访问。</li>
     */
    static boolean fallbackCandidate(CustomerAccount account, CustomerPassword password, ClientSession session,
        ClientMember member, Instant now) {
        return session != null && session.contextType() == ClientSessionContextType.TENANT
            && usable(account, password, session, now) && !(boundMember(session, member) && accessible(member));
    }

    /** 降级的公开原因码：成员已不属于该会话时按成员失效处理。 */
    static String reasonOf(ClientSession session, ClientMember member) {
        return boundMember(session, member) ? reason(member) : "MEMBER_INACTIVE";
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
        return validContext(context.getAccount(), credentials.password(context.getPlan().accountId()),
            context.getSession(), lockBoundMember(context.getSession()), context.getNow());
    }

    /**
     * 提交前确认本次刷新应降级为账号上下文
     * <li>按锁定后的成员与租户重证；当前刷新代、代号、期限和会话版本必须与准备时一致。</li>
     */
    boolean fallbackRefresh(RefreshClientContext context) {
        var plan = context.getPlan();
        var session = context.getSession();
        var refresh = context.getRefresh();
        return plan.fallback() != null && refresh.status() == OperationsRefreshStatus.ISSUED
            && refresh.generationNo() == session.generationNo() && context.getNow().isBefore(refresh.expiresTime())
            && session.version() == plan.expectedSessionVersion()
            && fallbackCandidate(context.getAccount(), credentials.password(plan.accountId()), session,
                lockBoundMember(session), context.getNow());
    }

    /** 事务内以共享锁读取租户会话绑定的成员；账号上下文为空。 */
    ClientMember lockBoundMember(ClientSession session) {
        return session != null && session.contextType() == ClientSessionContextType.TENANT && session.memberId() != null
            ? members.lockMember(session.memberId()) : null;
    }

    /**
     * 准备切换上下文：当前会话有效后，按目标成员预签名新会话令牌
     * <li>目标成员不存在、不属于本账号或不可访问时统一拒绝，不区分原因。</li>
     * <li>新会话绝对到期沿用当前会话；Cookie 秘密证明在事务内核对。</li>
     */
    SwitchPlan prepareSwitch(long accountId, long sessionId, String token, Long memberId) {
        LogoutProof proof = prepareLogout(accountId, sessionId, token);
        var account = accounts.account(accountId);
        var session = sessions.session(sessionId);
        if (proof == null || !usable(account, credentials.password(accountId), session, clock.instant())) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        if (memberId == null) {
            Issue issue = tokens.prepare(accountContext(account, ids.nextId(), null), account.authEpoch(),
                account.credentialVersion(), session.absoluteExpireTime(), 1);
            return new SwitchPlan(proof, null, null, issue);
        }
        ClientMember member = members.member(memberId);
        if (!targetAllowed(member, accountId)) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        Issue issue = tokens.prepare(tenantContext(account, ids.nextId(), member), account.authEpoch(),
            account.credentialVersion(), session.absoluteExpireTime(), 1);
        return new SwitchPlan(proof, memberId, member.tenantId(), issue);
    }

    /** 目标成员属于本账号且成员、租户当前均允许访问。 */
    static boolean targetAllowed(ClientMember member, long accountId) {
        return member != null && member.accountId() == accountId && accessible(member);
    }

    /** 按账号、会话、刷新代、目标成员的固定锁序读取当前事实；Cookie 证明不符返回空。 */
    SwitchClientContext currentSwitch(SwitchPlan plan) {
        CustomerAccount account = accounts.lockAccount(plan.proof().accountId());
        LogoutClientContext current = currentLogout(plan.proof());
        if (current == null) {
            return null;
        }
        ClientMember member = plan.memberId() == null ? null : members.lockMember(plan.memberId());
        return new SwitchClientContext(plan, account, current.getSession(), current.getRefresh(), member,
            current.getNow());
    }

    /** 提交前重证当前会话本身及其活动刷新代仍可用于切换。 */
    boolean switchUsable(SwitchClientContext context) {
        var session = context.getSession();
        var refresh = context.getRefresh();
        return usable(context.getAccount(), credentials.password(context.getAccount().id()), session, context.getNow())
            && refresh.status() == OperationsRefreshStatus.ISSUED && refresh.generationNo() == session.generationNo()
            && context.getNow().isBefore(refresh.expiresTime());
    }

    /** 提交前重证目标成员；租户必须与准备时一致。 */
    boolean targetAllowed(SwitchClientContext context) {
        var member = context.getMember();
        return context.toAccount() || targetAllowed(member, context.getAccount().id())
            && member.tenantId() == context.getPlan().tenantId();
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
