package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsSessionPolicy;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.*;
import com.paytech.platform.support.accesscontrol.domain.enums.LoginFailureReason;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.*;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.OperationsContextType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;

import lombok.AllArgsConstructor;

/** Operations 认证的事务外准备与提交前事实比较；不写库。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsAuthenticationPreparation {
    private final IdGenerator ids;
    private final Clock clock;
    private final LoginEmailVault vault;
    private final OperationsTokenPreparation tokens;
    private final OperationsMemberRepository members;
    private final OperationsAccountRepository accounts;
    private final OperationsSessionService sessions;
    private final OperationsPasswordPolicy passwords;
    private final OperationsRefreshRepository refreshes;
    private final OperationsPasswordRepository credentials;
    private final OperationsSessionRepository sessionRows;
    private final OperationsResourceKeyPreparation resourceKeys;

    /** 解析真实账号或批准的未知登录 HMAC 桶。 */
    public LoginResolution resolveLogin(LoginCommand request) {
        String identifier = request.loginIdentifier();
        if ("admin".equals(identifier)) {
            return loginResolution(accounts.loginAccount(null, true), identifier);
        }
        String canonical;
        try {
            canonical = passwords.canonicalEmail(identifier);
        } catch (FailException exception) {
            return loginResolution(null, identifier);
        }
        OperationsAccount account = accounts.loginAccount(vault.protectCanonicalEmail(canonical), false);
        return loginResolution(account, canonical);
    }

    /** 只以稳定账号或受控 HMAC 派生资源键。 */
    LoginResolution loginResolution(OperationsAccount account, String unknownIdentifier) {
        return account == null ? new LoginResolution(null, resourceKeys.unknownLoginSegment(unknownIdentifier))
            : new LoginResolution(account.id(), resourceKeys.accountSegment(Long.toString(account.id())));
    }

    /** 事务外完成密码计算和新会话令牌预签。 */
    public PreparedLogin prepareLogin(long accountId, LoginCommand request) {
        Instant preparedAt = now();
        OperationsAccount account = accounts.account(accountId);
        if (account == null) {
            passwords.matchesUnknownAccount(request.password());
            return rejectedLogin(null, null, null, LoginFailureReason.UNKNOWN_ACCOUNT, preparedAt);
        }
        OperationsPassword password = credentials.password(account.id());
        if (account.status() != OperationsAccountStatus.ACTIVE) {
            passwords.matchesUnknownAccount(request.password());
            return rejectedLogin(account, password, null, LoginFailureReason.ACCOUNT_DISABLED, preparedAt);
        }
        if (password == null || password.status() != OperationsCredentialStatus.ACTIVE) {
            passwords.matchesUnknownAccount(request.password());
            return rejectedLogin(account, password, null, LoginFailureReason.CREDENTIAL_UNAVAILABLE, preparedAt);
        }
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(preparedAt)) {
            passwords.matchesUnknownAccount(request.password());
            return rejectedLogin(account, password, null, LoginFailureReason.ACCOUNT_LOCKED, preparedAt);
        }
        validateCredential(account, password);
        if (!passwords.matches(request.password(), password.verifier())) {
            return rejectedLogin(account, password, null, LoginFailureReason.PASSWORD_MISMATCH, preparedAt);
        }
        OperationsMember member = account.type() == OperationsAccountType.HUMAN ? members.member(account.id()) : null;
        if (account.type() == OperationsAccountType.HUMAN && (member == null || !sessions.isActive(member))) {
            return rejectedLogin(account, password, member, LoginFailureReason.MEMBER_TENANT_UNAVAILABLE, preparedAt);
        }
        OperationsSession session = newSession(account, member, preparedAt);
        PreparedTokens preparedTokens = tokens.prepare(account, password, session, 1, preparedAt);
        return new PreparedLogin(account, password, member, session, preparedTokens, null, preparedAt);
    }

    /** 构造受 Realm/Channel 约束的新会话。 */
    OperationsSession newSession(OperationsAccount account, OperationsMember member, Instant now) {
        return new OperationsSession(ids.nextId(), account.id(),
            member == null ? OperationsContextType.BOOTSTRAP : OperationsContextType.TENANT,
            member == null ? null : member.tenantId(), member == null ? null : member.id(), account.authEpoch(),
            account.credentialVersion(), now, now, now.plusSeconds(1800), now.plusSeconds(28800), 1,
            OperationsSessionStatus.ACTIVE);
    }

    /** 冻结拒绝原因，密码失败计数由后续本地事务保存。 */
    PreparedLogin rejectedLogin(OperationsAccount account, OperationsPassword password, OperationsMember member,
        LoginFailureReason reason, Instant preparedAt) {
        return new PreparedLogin(account, password, member, null, null, reason, preparedAt);
    }

    /** 按当前数据库事实复核登录前的安全快照。 */
    boolean sameLoginSnapshot(PreparedLogin prepared, OperationsAccount account, OperationsPassword password,
        OperationsMember member, Instant commitAt) {
        OperationsAccount expected = prepared.account();
        OperationsPassword expectedPassword = prepared.password();
        if (account == null || password == null || account.status() != OperationsAccountStatus.ACTIVE
            || password.status() != OperationsCredentialStatus.ACTIVE || account.id() != expected.id()
            || account.version() != expected.version() || account.credentialVersion() != expected.credentialVersion()
            || account.authEpoch() != expected.authEpoch()
            || password.credentialVersion() != expectedPassword.credentialVersion()
            || !password.verifier().equals(expectedPassword.verifier())
            || account.lockedUntil() != null && account.lockedUntil().isAfter(commitAt)) {
            return false;
        }
        if (expected.type() != OperationsAccountType.HUMAN) {
            return member == null;
        }
        OperationsMember expectedMember = prepared.member();
        return member != null && expectedMember != null && member.id() == expectedMember.id()
            && member.tenantId() == expectedMember.tenantId() && sessions.isActive(member);
    }

    /** 提交时确定初始空闲期限，绝对期限保持不变。 */
    OperationsSession committedSession(OperationsSession prepared, Instant commitAt) {
        Instant idleExpireTime = commitAt.plusSeconds(1800).isBefore(prepared.absoluteExpireTime())
            ? commitAt.plusSeconds(1800) : prepared.absoluteExpireTime();
        return new OperationsSession(prepared.id(), prepared.accountId(), prepared.contextType(), prepared.tenantId(),
            prepared.memberId(), prepared.accountEpoch(), prepared.credentialVersion(), commitAt, commitAt,
            idleExpireTime, prepared.absoluteExpireTime(), prepared.generationNo(), prepared.status());
    }

    /** 拒绝准备后已经变化的账号。 */
    ConcurrentRequestException concurrentLogin() {
        return new ConcurrentRequestException("账号安全状态已变化，请重新登录");
    }

    /** 校验已保存 PHC 与算法参数一致。 */
    void validateCredential(OperationsAccount account, OperationsPassword password) {
        String prefix = "$argon2id$v=" + password.algorithmVersion() + "$m=" + password.memoryKib() + ",t="
            + password.iterations() + ",p=" + password.parallelism() + "$";
        if (password.credentialVersion() != account.credentialVersion() || password.algorithmVersion() != 19
            || password.memoryKib() < 19456 || password.memoryKib() > 262144 || password.iterations() < 2
            || password.iterations() > 10 || password.parallelism() != 1 || !password.verifier().startsWith(prefix)) {
            throw new com.paytech.foundation.common.error.ErrorException("Operations Credential算法/参数与PHC不一致");
        }
    }

    /** 解析刷新代所属会话或批准的未知尝试桶。 */
    public RefreshResolution resolveRefresh(RefreshCommand request) {
        OperationsRefresh refresh = findRefresh(request.refreshToken());
        return refresh == null ? new RefreshResolution(null, resourceKeys.unknownRefreshSegment(request.refreshToken()))
            : new RefreshResolution(refresh.sessionId(),
                resourceKeys.sessionSegment(Long.toString(refresh.sessionId())));
    }

    /** 事务外验证刷新秘密并预签后继，不在此处写库。 */
    public PreparedRefresh prepareRefresh(long sessionId, RefreshCommand request) {
        Instant preparedAt = now();
        OperationsRefresh refresh = findRefresh(request.refreshToken());
        if (refresh == null || refresh.sessionId() != sessionId
            || !tokens.matchesRefresh(request.refreshToken(), refresh)) {
            return null;
        }
        OperationsSession session = sessionRows.session(sessionId);
        OperationsAccount account = session == null ? null : accounts.account(session.accountId());
        OperationsPassword password = account == null ? null : credentials.password(account.id());
        OperationsMember member = account == null || account.type() == OperationsAccountType.BOOTSTRAP_ADMIN ? null
            : members.member(account.id());
        PreparedTokens preparedTokens = null;
        if (refresh.status() == OperationsRefreshStatus.ISSUED && preparedAt.isBefore(refresh.expiresTime())
            && session != null && refresh.generationNo() == session.generationNo()) {
            try {
                OperationsSessionPolicy.requireValid(account, password, session, member, preparedAt);
            } catch (FailException exception) {
                // 安全事实将在 Session X 后重读；此处仅避免为已无效会话预签。
                return new PreparedRefresh(refresh, session, account, password, member, null, preparedAt);
            }
            preparedTokens = tokens.prepare(account, password, session, session.generationNo() + 1, preparedAt);
        }
        return new PreparedRefresh(refresh, session, account, password, member, preparedTokens, preparedAt);
    }

    /** 只通过有界合法查找段读取刷新代。 */
    OperationsRefresh findRefresh(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}")) {
            return null;
        }
        return refreshes.refresh(token.substring(0, token.indexOf('.')));
    }

    /** 当前刷新代必须仍为已证明持有的原令牌。 */
    boolean sameRefreshSnapshot(OperationsRefresh expected, OperationsRefresh actual) {
        return actual != null && expected.id() == actual.id() && expected.sessionId() == actual.sessionId()
            && expected.generationNo() == actual.generationNo() && expected.lookupId().equals(actual.lookupId())
            && expected.secretHmac().equals(actual.secretHmac()) && expected.keyVersion() == actual.keyVersion()
            && expected.expiresTime().equals(actual.expiresTime());
    }

    /** 签名前后的会话、账号和成员安全事实必须一致。 */
    boolean sameRefreshSecuritySnapshot(PreparedRefresh prepared, OperationsSession session, OperationsAccount account,
        OperationsPassword password, OperationsMember member) {
        OperationsSession expectedSession = prepared.session();
        OperationsAccount expectedAccount = prepared.account();
        OperationsPassword expectedPassword = prepared.password();
        if (expectedSession == null || expectedAccount == null || expectedPassword == null
            || expectedSession.id() != session.id() || expectedSession.accountId() != session.accountId()
            || expectedSession.accountEpoch() != session.accountEpoch()
            || expectedSession.credentialVersion() != session.credentialVersion()
            || expectedSession.generationNo() != session.generationNo() || expectedSession.status() != session.status()
            || expectedSession.contextType() != session.contextType()
            || !expectedSession.absoluteExpireTime().equals(session.absoluteExpireTime())
            || !expectedSession.idleExpireTime().equals(session.idleExpireTime())
            || !java.util.Objects.equals(expectedSession.tenantId(), session.tenantId())
            || !java.util.Objects.equals(expectedSession.memberId(), session.memberId())
            || expectedAccount.id() != account.id() || expectedAccount.type() != account.type()
            || expectedAccount.status() != account.status() || expectedAccount.authEpoch() != account.authEpoch()
            || expectedAccount.credentialVersion() != account.credentialVersion()
            || expectedPassword.accountId() != password.accountId()
            || expectedPassword.credentialVersion() != password.credentialVersion()
            || expectedPassword.status() != password.status()
            || expectedPassword.forceChange() != password.forceChange()) {
            return false;
        }
        OperationsMember expectedMember = prepared.member();
        return expectedMember == null && member == null
            || expectedMember != null && member != null && expectedMember.id() == member.id()
                && expectedMember.accountId() == member.accountId() && expectedMember.tenantId() == member.tenantId()
                && expectedMember.status() == member.status() && expectedMember.ownerStatus() == member.ownerStatus()
                && expectedMember.securityStatus() == member.securityStatus();
    }

    /** 拒绝准备后已经变化的会话。 */
    ConcurrentRequestException concurrentRefresh() {
        return new ConcurrentRequestException("Session安全状态已变化，请重新认证");
    }

    /** 事务外证明退出请求持有同一会话的刷新秘密。 */
    public PreparedLogout prepareLogout(String accountId, String sessionId, RefreshCommand request) {
        long account = sessions.parseId(accountId);
        OperationsSession session = sessionRows.session(sessions.parseId(sessionId));
        OperationsRefresh refresh = findRefresh(request.refreshToken());
        if (session == null || session.accountId() != account || refresh == null || refresh.sessionId() != session.id()
            || !tokens.matchesRefresh(request.refreshToken(), refresh)) {
            throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
        }
        return new PreparedLogout(account, session, refresh, now());
    }

    /** 事务外验证旧密码、读取邮箱并派生新密码。 */
    public PreparedPasswordChange preparePasswordChange(String accountId, String sessionId,
        ChangePasswordCommand request) {
        var current = sessions.requireCurrent(accountId, sessionId);
        validateCredential(current.account(), current.password());
        if (!passwords.matches(request.currentPassword(), current.password().verifier())) {
            throw new FailException(OperationsErrorCode.OPERATIONS_AUTHENTICATION_FAILED);
        }
        String email = current.account().type() == OperationsAccountType.HUMAN
            ? vault.revealCanonicalEmail(current.account().emailToken()) : "admin";
        String value = passwords.validateNewPassword(request.newPassword(), email);
        if (passwords.matches(value, current.password().verifier())) {
            throw new BizCheckFailException("新密码不能与当前密码相同");
        }
        return new PreparedPasswordChange(current.account(), current.password(), current.session(),
            passwords.encode(value), now());
    }

    /** 改密提交时复核原认证快照。 */
    boolean samePasswordChangeSnapshot(PreparedPasswordChange prepared, OperationsAccount account,
        OperationsPassword password, OperationsSession session) {
        OperationsAccount expected = prepared.account();
        OperationsPassword expectedPassword = prepared.password();
        OperationsSession expectedSession = prepared.session();
        return account != null && password != null && session != null && account.id() == expected.id()
            && account.type() == expected.type() && account.status() == expected.status()
            && account.version() == expected.version() && account.credentialVersion() == expected.credentialVersion()
            && account.authEpoch() == expected.authEpoch() && password.accountId() == expectedPassword.accountId()
            && password.credentialVersion() == expectedPassword.credentialVersion()
            && password.status() == expectedPassword.status()
            && password.forceChange() == expectedPassword.forceChange()
            && password.algorithmVersion() == expectedPassword.algorithmVersion()
            && password.memoryKib() == expectedPassword.memoryKib()
            && password.iterations() == expectedPassword.iterations()
            && password.parallelism() == expectedPassword.parallelism()
            && password.verifier().equals(expectedPassword.verifier()) && session.id() == expectedSession.id()
            && session.accountId() == expectedSession.accountId() && session.status() == expectedSession.status()
            && session.accountEpoch() == expectedSession.accountEpoch()
            && session.credentialVersion() == expectedSession.credentialVersion();
    }

    /** 拒绝准备后已经变化的改密资格。 */
    ConcurrentRequestException concurrentPasswordChange() {
        return new ConcurrentRequestException("账号安全状态已变化，请重新登录");
    }

    /** 以 UTC 微秒记录本次认证时点。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 对未知账号执行等价密码计算，不生成可查询账号差异。 */
    public void rejectUnknownLogin(String password) {
        passwords.matchesUnknownAccount(password);
    }
}
