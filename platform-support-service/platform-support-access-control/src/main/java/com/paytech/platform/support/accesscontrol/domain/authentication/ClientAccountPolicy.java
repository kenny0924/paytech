package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerPassword;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsCredentialStatus;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

/** Client 账号、会话与密码的无副作用规则。 */
public final class ClientAccountPolicy {
    /** 纯规则工具不可实例化。 */
    private ClientAccountPolicy() {}

    /** 本切片只签发 ACCOUNT 上下文，Tenant 切换需另行经过成员资格用例。 */
    public static boolean valid(CustomerAccount account, CustomerPassword password, ClientSession session,
        Instant now) {
        return eligible(account, password) && session != null && session.accountId() == account.id()
            && session.contextType() == ClientSessionContextType.ACCOUNT && session.tenantId() == null
            && session.memberId() == null
            && ClientSessionRules.valid(session.state(), account.authEpoch(), account.credentialVersion(), now);
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

    /** 生成账号上下文的最小可见事实。 */
    public static ClientAccountView toContext(CustomerAccount account, ClientSession session) {
        return new ClientAccountView(account.id(), session.id(), account.emailMasked(), true,
            session.contextType().getCode(), session.tenantId(), session.memberId(), List.of("ONBOARDING", "LOGOUT"));
    }

    /** HMAC 比较不依赖字符串首个不同字符的位置。 */
    public static boolean matches(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
            right.getBytes(StandardCharsets.US_ASCII));
    }
}
