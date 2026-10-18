package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

/** Client 账号、会话、工作区成员与密码的无副作用规则。 */
public final class ClientAccountPolicy {
    /** 账号上下文允许的动作；入驻只在账号上下文进行。 */
    public static final List<String> ACCOUNT_ACTIONS = List.of("ONBOARDING", "SELECT_WORKSPACE", "LOGOUT");

    /** 租户上下文允许的动作。 */
    public static final List<String> TENANT_ACTIONS = List.of("WORKSPACE", "SELECT_WORKSPACE", "ACCOUNT_CONTEXT", "LOGOUT");

    /** 纯规则工具不可实例化。 */
    private ClientAccountPolicy() {}

    /** 只接受账号上下文会话；供入驻文件等仅限账号上下文的既有消费者使用。 */
    public static boolean valid(CustomerAccount account, CustomerPassword password, ClientSession session,
        Instant now) {
        return usable(account, password, session, now) && session.contextType() == ClientSessionContextType.ACCOUNT
            && session.tenantId() == null && session.memberId() == null;
    }

    /**
     * 账号或租户上下文会话当前有效
     * <li>租户上下文还要求会话绑定的成员仍属于该账号、成员与租户当前均允许访问。</li>
     */
    public static boolean validContext(CustomerAccount account, CustomerPassword password, ClientSession session,
        ClientMember member, Instant now) {
        if (!usable(account, password, session, now)) {
            return false;
        }
        if (session.contextType() == ClientSessionContextType.ACCOUNT) {
            return session.tenantId() == null && session.memberId() == null;
        }
        return boundMember(session, member) && accessible(member);
    }

    /** 账号仍可登录且会话本身（状态、安全纪元、凭据版本、期限）有效，不判断上下文。 */
    public static boolean usable(CustomerAccount account, CustomerPassword password, ClientSession session,
        Instant now) {
        return eligible(account, password) && session != null && session.accountId() == account.id()
            && ClientSessionRules.valid(session.state(), account.authEpoch(), account.credentialVersion(), now);
    }

    /** 成员正是租户会话绑定的成员与租户，且属于会话账号。 */
    public static boolean boundMember(ClientSession session, ClientMember member) {
        return member != null && session.memberId() != null && session.tenantId() != null
            && member.id() == session.memberId() && member.tenantId() == session.tenantId()
            && member.accountId() == session.accountId();
    }

    /** 成员 ACTIVE，租户来源状态与安全状态均为 ACTIVE。 */
    public static boolean accessible(ClientMember member) {
        return member != null && member.status() == OperationsMemberStatus.ACTIVE
            && member.ownerStatus() == OperationsTenantOwnerStatus.ACTIVE
            && member.securityStatus() == OperationsTenantSecurityStatus.ACTIVE;
    }

    /**
     * 不可访问时的第一个公开原因码；可访问返回空
     * <li>运营填写的内部暂停理由不进入原因码。</li>
     */
    public static String reason(ClientMember member) {
        if (member == null) {
            return "MEMBER_INACTIVE";
        }
        if (member.ownerStatus() == OperationsTenantOwnerStatus.TERMINATED) {
            return "TENANT_TERMINATED";
        }
        if (member.ownerStatus() != OperationsTenantOwnerStatus.ACTIVE) {
            return "TENANT_SUSPENDED";
        }
        if (member.securityStatus() != OperationsTenantSecurityStatus.ACTIVE) {
            return "TENANT_SECURITY_SUSPENDED";
        }
        if (member.status() != OperationsMemberStatus.ACTIVE) {
            return "MEMBER_INACTIVE";
        }
        return null;
    }

    /** 账号和密码属于同一 CUSTOMER 身份且凭据快照一致。 */
    public static boolean eligible(CustomerAccount account, CustomerPassword password) {
        return account != null && account.status() == OperationsAccountStatus.ACTIVE && account.verifiedTime() != null
            && password != null && password.accountId() == account.id()
            && password.status() == OperationsCredentialStatus.ACTIVE && !password.forceChange()
            && password.credentialVersion() == account.credentialVersion();
    }

    /** 沿用现有 Argon2id 合同，不能让损坏的 PHC 触发无限制计算。 */
    public static void validateCredential(CustomerPassword password) {
        String prefix = "$argon2id$v=" + password.algorithmVersion() + "$m=" + password.memoryKib() + ",t="
            + password.iterations() + ",p=" + password.parallelism() + "$";
        if (password.algorithmVersion() != 19 || password.memoryKib() < 19456 || password.memoryKib() > 262144
            || password.iterations() < 2 || password.iterations() > 10 || password.parallelism() != 1
            || password.verifier() == null || !password.verifier().startsWith(prefix)) {
            throw new ServiceUnavailableException();
        }
    }

    /** 生成会话上下文的最小可见事实，允许动作按上下文区分。 */
    public static ClientAccountView toContext(CustomerAccount account, ClientSession session) {
        return new ClientAccountView(account.id(), session.id(), account.emailMasked(), true,
            session.contextType().getCode(), session.tenantId(), session.memberId(), actions(session.contextType()));
    }

    /** 新会话的账号上下文投影；降级时携带公开原因码。 */
    public static ClientAccountView accountContext(CustomerAccount account, long sessionId, String fallbackReason) {
        return new ClientAccountView(account.id(), sessionId, account.emailMasked(), true,
            ClientSessionContextType.ACCOUNT.getCode(), null, null, ACCOUNT_ACTIONS, fallbackReason);
    }

    /** 新会话的租户上下文投影。 */
    public static ClientAccountView tenantContext(CustomerAccount account, long sessionId, ClientMember member) {
        return new ClientAccountView(account.id(), sessionId, account.emailMasked(), true,
            ClientSessionContextType.TENANT.getCode(), member.tenantId(), member.id(), TENANT_ACTIONS);
    }

    /** 上下文对应的允许动作。 */
    static List<String> actions(ClientSessionContextType type) {
        return type == ClientSessionContextType.TENANT ? TENANT_ACTIONS : ACCOUNT_ACTIONS;
    }

    /** HMAC 比较不依赖字符串首个不同字符的位置。 */
    public static boolean matches(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
            right.getBytes(StandardCharsets.US_ASCII));
    }
}
