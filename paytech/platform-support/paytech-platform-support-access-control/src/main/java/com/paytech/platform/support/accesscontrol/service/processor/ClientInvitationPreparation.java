package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.InvitationEmail;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.InvitationProof;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.IssuedLink;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationResult;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.InvitationChallengeRepository;
import com.paytech.platform.support.accesscontrol.integration.mail.AuthenticationEmailSender;
import com.paytech.platform.support.accesscontrol.integration.signing.HumanAuthenticationCryptography;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;

/**
 * 邀请用例共用的事务外准备：邮箱 Vault Token、链接与 HMAC、链接证明核对、提交后发信，以及资源锁
 * <li>本类不写库；Vault、HMAC 与发信都不得在数据库事务内调用。</li>
 * <li>HMAC 与注册、重置共用同一密钥用途和长度前缀输入，挑战范围按邀请计算。</li>
 */
@Component
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientInvitationPreparation {
    /** 与注册、重置挑战相同的 HMAC 用途，保证同一密钥轮换规则。 */
    static final String PROOF_PURPOSE = "client-email-challenge-v1";

    private final Clock clock;
    private final IdGenerator ids;
    private final LoginEmailVault vault;
    private final OperationsPasswordPolicy passwords;
    private final ClientEmailChallengePolicy policy;
    private final AuthenticationEmailSender emailSender;
    private final HumanAuthenticationCryptography crypto;
    private final InvitationChallengeRepository challenges;
    private final ClientAuthenticationProperties properties;

    /** 绑定 CUSTOMER 独立的邮箱 Vault 与 HMAC 端口。 */
    public ClientInvitationPreparation(Clock clock, IdGenerator ids,
        @Qualifier("clientLoginEmailVault") LoginEmailVault vault, OperationsPasswordPolicy passwords,
        ClientEmailChallengePolicy policy, AuthenticationEmailSender emailSender,
        @Qualifier("clientAuthenticationCryptography") HumanAuthenticationCryptography crypto,
        InvitationChallengeRepository challenges, ClientAuthenticationProperties properties) {
        this.clock = clock;
        this.ids = ids;
        this.vault = vault;
        this.passwords = passwords;
        this.policy = policy;
        this.emailSender = emailSender;
        this.crypto = crypto;
        this.challenges = challenges;
        this.properties = properties;
    }

    /** 按登录邮箱规则规范化，格式错误返回参数错误；不访问外部依赖。 */
    String canonicalEmail(String email) {
        return passwords.canonicalEmail(email);
    }

    /** 以 STABLE 模式把规范邮箱换成 Token，与账号 email_vault_token 可直接比较。 */
    InvitationEmail protect(String canonical) {
        return new InvitationEmail(canonical, vault.protectCanonicalEmail(canonical), passwords.maskEmail(canonical));
    }

    /** 重发时取回规范邮箱原值；只用于本次发信。 */
    String reveal(String token) {
        return vault.revealCanonicalEmail(token);
    }

    /** 为邀请签发新链接并计算 HMAC。 */
    IssuedLink issue(long invitationId) {
        LinkMaterial link = policy.newLink();
        String scope = policy.invitationScopeHash(invitationId);
        var hmac = crypto.versionedHmac(PROOF_PURPOSE,
            policy.proofInput(link.challengeNo(), ClientChallengePurpose.TENANT_INVITATION_ACCEPT, scope, link.secret()));
        return new IssuedLink(ids.nextId(), link, scope, hmac.digest(), hmac.keyVersion());
    }

    /** 新挑战对象；到期与邀请一致，插入前准备全部初始值。 */
    InvitationChallenge newChallenge(IssuedLink issued, long invitationId, String emailToken, String emailMasked,
        Instant expiresTime, Long supersedesId, Instant now) {
        return new InvitationChallenge(issued.challengeId(), issued.link().challengeNo(), invitationId, emailToken,
            emailMasked, issued.scopeHash(), issued.secretHmac(), issued.keyVersion(), expiresTime, 5, 0,
            ClientChallengeStatus.PENDING, supersedesId, 1, now);
    }

    /**
     * 核对链接证明
     * <li>格式错误或挑战不存在返回 CLIENT_INVITATION_INVALID，不计尝试。</li>
     * <li>秘密不正确时仍返回证明（secretMatches=false），由调用方在锁内记一次失败后拒绝。</li>
     */
    InvitationProof prove(String challengeNo, String secret) {
        if (!policy.validLink(challengeNo, secret)) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        var challenge = challenges.challenge(challengeNo);
        if (challenge == null) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        var hmac = crypto.versionedHmac(PROOF_PURPOSE, policy.proofInput(challengeNo,
            ClientChallengePurpose.TENANT_INVITATION_ACCEPT, challenge.scopeHash(), secret));
        if (hmac.keyVersion() != challenge.keyVersion()) {
            throw new ServiceUnavailableException();
        }
        boolean matches = challenge.status() != ClientChallengeStatus.SUPERSEDED
            && policy.matchesSecret(challenge.state(), hmac.digest(), hmac.keyVersion());
        return new InvitationProof(challenge.id(), challenge.invitationId(), matches);
    }

    /** 提交后同步发信；失败不回滚邀请，返回 FAILED 供界面提示重发。 */
    String deliver(String canonicalEmail, LinkMaterial link) {
        try {
            emailSender.send(canonicalEmail, ClientChallengePurpose.TENANT_INVITATION_ACCEPT, link);
            return TenantInvitationResult.EMAIL_SENT;
        } catch (ServiceUnavailableException unavailable) {
            // 发送器已计指标且不记录收件人与链接；客户重发会替代本链接。
            return TenantInvitationResult.EMAIL_FAILED;
        }
    }

    /** 当前 UTC 微秒时间。 */
    Instant now() {
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    /** 新的不可枚举邀请编号。 */
    String invitationNo() {
        return java.util.UUID.randomUUID().toString();
    }

    /** 新的主键。 */
    long nextId() {
        return ids.nextId();
    }

    /**
     * 多资源锁使用的完整逻辑 Key：与单资源锁自动添加的 lock: 前缀一致，保证同一资源的不同入口互斥
     */
    static String fullLockKey(String key) {
        return "lock:" + key;
    }

    /** 邀请资源锁键；重发、撤销与接受共用。 */
    static String invitationLock(long invitationId) {
        return "client-invitation:" + invitationId;
    }

    /** 成员资源锁键；调整角色、移除与恢复已有成员共用。 */
    static String memberLock(long tenantId, long memberId) {
        return "tenant-member:" + tenantId + ":" + memberId;
    }

    /** 账号资源锁键，与注册验证、密码重置共用。 */
    static String accountLock(long accountId) {
        return "customer:account:" + accountId;
    }

    /** 资源锁等待上限，沿用 Client 认证配置。 */
    java.time.Duration lockWait() {
        return properties.getLockWaitTime();
    }

    /** 资源锁租约，沿用 Client 认证配置。 */
    java.time.Duration lockLease() {
        return properties.getLockLeaseTime();
    }
}
